package forex.programs.cache

import java.util.concurrent.TimeUnit

import cats.Monad
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import forex.programs.cache.errors.Error
import forex.programs.cache.errors.Error.{CacheRefreshOneFrameError, CacheRefreshParseResponseFailed, CacheRefreshRequestFailed, CacheRefreshTimeoutExceeded}
import forex.services.history.RateHistoryCall
import forex.domain.types.RateTypes._
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object AutoRetryCall {

  def performRetryRefreshCall[F[_]: Concurrent: Timer: ContextShift: Logger]
    (getRatesForAllPairsCall: F[Either[Error, RatesList]],
     historyAddCall: RateHistoryCall => F[Unit])
    (implicit stateRef: RatesStateRef[F], refreshTimeout: FiniteDuration): F[RatesMap] = {

    for {
      callTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      getRatesResult <- getRatesForAllPairsCall
      ratesMap <- getRatesResult match {
        case Right(ratesList) =>
          for {
            rateSuccessCall <- RateHistoryCall(callTime, s"Rates successfully obtained, for count = [${ratesList.size}]", Some(ratesList)).pure[F]
            _ <- Concurrent[F].start(historyAddCall(rateSuccessCall))
            ratesMap <- ratesList.asMap.pure[F]
          } yield ratesMap
        case Left(error: Error) =>
          for {
            rateErrorCall <- RateHistoryCall(callTime, s"Error when obtaining rates = $error", None).pure[F]
            _ <- Concurrent[F].start(historyAddCall(rateErrorCall))
            _ <- Logger[F].error(s"Error when perform refresh rates, error = [$error]")
            retryTimeout <- error match {
              case CacheRefreshParseResponseFailed(_) | CacheRefreshRequestFailed(_) =>
                nextRetryTimeout[F](refreshTimeout, 2.seconds)
              case CacheRefreshTimeoutExceeded(_) =>
                nextRetryTimeout[F](refreshTimeout, 5.seconds)
              case CacheRefreshOneFrameError(_) =>
                nextRetryTimeout[F](refreshTimeout, refreshTimeout)
            }
            _ <- Logger[F].warn(s"Schedule next refresh with retry timeout = [$retryTimeout]")
            _ <- Timer[F].sleep(retryTimeout)
            ratesMap <- ContextShift[F].shift *> performRetryRefreshCall(getRatesForAllPairsCall, historyAddCall)
          } yield ratesMap
      }
    } yield ratesMap
  }

  def nextRetryTimeout[F[_]: Monad](refreshTimeout: FiniteDuration,
                                    defaultTimeout: FiniteDuration)
                                   (implicit stateRef: RatesStateRef[F]): F[FiniteDuration] = {

    def getNextTimeout(currentTimeout: FiniteDuration): FiniteDuration = {
      val factor = if (currentTimeout.lt(1.second)) 5 else if (currentTimeout.lt(10.second)) 2 else 1.5
      val nextTimeout = (currentTimeout.toMillis * factor).ceil.toInt.milliseconds
      if (nextTimeout.gt(refreshTimeout)) refreshTimeout else nextTimeout
    }

    for {
      timeout <- stateRef.nextRetryAfter.tryTake.map(_.getOrElse(defaultTimeout))
      _ <- stateRef.nextRetryAfter.tryPut(getNextTimeout(timeout))
    } yield timeout
  }
}
