package forex.programs
package cache

import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import errors._
import forex.common.datetime.DateTimeConverters
import forex.config.CacheConfig
import forex.domain.Currency
import forex.domain.types.RateTypes._
import forex.programs.cache.CacheState.{CacheUUID, RatesCache}
import forex.services.{CallsHistoryService, RatesService}
import forex.programs.cache.errors.Error.{CacheRefreshOneFrameError, CacheRefreshParseResponseFailed, CacheRefreshRequestFailed, CacheRefreshTimeoutExceeded}
import forex.services.history.model.RateHistoryCall
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class CacheProgram[F[_]: Concurrent: Timer: ContextShift: Logger](cacheConfig: CacheConfig,
                                                                  ratesService: RatesService[F],
                                                                  historyService: CallsHistoryService[F],
                                                                  cacheState: CacheState[F],
                                                                  blockingEC: ExecutionContextExecutor) extends Algebra[F] {

  val errorOnTimeout: Error = CacheRefreshTimeoutExceeded("Cash refresh timeout exceeded")

  implicit val refreshTimeout: FiniteDuration = cacheConfig.refreshTimeout

  override def startAutoRefreshableCache(): F[Unit] =
    for {
      _ <- Concurrent[F].start(performRefresh(isEager = true)(cacheState))
    } yield ()

  override def obtainCachedMap: F[RatesMap] = {
    for {
      ratesCache <- cacheState.ratesCache.get
      _ <- ratesCache.calls.release
      ratesMap <- ratesCache.ratesMap.get
    } yield ratesMap
  }

  private def performRefresh(isEager: Boolean)
                            (implicit cacheState: CacheState[F]): F[Unit] =
    for {
      refreshTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
      _ <- Logger[F].debug(s"Perform refresh, isEager = [$isEager], time = [$refreshTime]")
      newRateCache <- if (isEager) {
        for {
          ratesMap <- ContextShift[F].shift *> performRetryRefreshCall()
          newRateCache <- RatesCache.empty
          _ <- newRateCache.ratesMap.complete(ratesMap)
          _ <- showLastCount() *> cacheState.ratesCache.set(newRateCache)
        } yield newRateCache
      } else {
        for {
          newRateCache <- RatesCache.empty
          _ <- showLastCount() *> cacheState.ratesCache.set(newRateCache)
          _ <- newRateCache.calls.acquire
          ratesMap <- ContextShift[F].shift *> performRetryRefreshCall()
          _ <- newRateCache.ratesMap.complete(ratesMap)
          _ <- newRateCache.calls.release
        } yield newRateCache
      }
      _ <- ContextShift[F].shift *> scheduleNextClean(cacheConfig.expirationTimeout, newRateCache.cacheUUID)
      _ <- cacheState.nextRefreshDuration.tryTake
      _ <- ContextShift[F].shift *> scheduleNextRefresh(cacheConfig.refreshTimeout)
    } yield ()

  private def showLastCount()(implicit cacheState: CacheState[F]): F[Unit] = {
    for {
      callsCount <- cacheState.ratesCache.get.flatMap(_.calls.available)
      _ <- Logger[F].debug(s"Last calls to previous cache, count = [$callsCount]")
    } yield ()
  }

  private def scheduleNextRefresh(scheduleDuration: FiniteDuration)
                                 (implicit cacheState: CacheState[F]): F[Unit] =
    for {
      _ <- Logger[F].debug(s"Schedule next refresh with timeout = [$scheduleDuration]")
      _ <- Timer[F].sleep(scheduleDuration)
      isAnyCalls <- cacheState.ratesCache.get.flatMap(_.calls.available.map(_ > 0))
      _ <- Concurrent[F].start(
        if (isAnyCalls) performRefresh(isEager = true) else performRefresh(isEager = false)
      )
    } yield ()

  private def scheduleNextClean(scheduleDuration: FiniteDuration, cacheUUID: CacheUUID)
                               (implicit cacheState: CacheState[F]): F[Unit] =
    for {
      _ <- Logger[F].debug(s"Schedule next clean with timeout = [$scheduleDuration]")
      _ <- Timer[F].sleep(scheduleDuration)
      ratesCache <- cacheState.ratesCache.get
      _ <- if (ratesCache.cacheUUID == cacheUUID) {
        for {
          _ <- Concurrent[F].start(performRefresh(isEager = false)).pure[F]
        } yield ()
      } else ().pure[F]
      nowTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      _ <- Concurrent[F].start(historyCleanCall(nowTime))
    } yield ()

  def performRetryRefreshCall()(implicit cacheState: CacheState[F],
                                refreshTimeout: FiniteDuration): F[RatesMap] = {

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
                nextRetryTimeout(refreshTimeout, 2.seconds)
              case CacheRefreshTimeoutExceeded(_) =>
                nextRetryTimeout(refreshTimeout, 5.seconds)
              case CacheRefreshOneFrameError(_) =>
                nextRetryTimeout(refreshTimeout, refreshTimeout)
            }
            _ <- Logger[F].warn(s"Schedule next refresh with retry timeout = [$retryTimeout]")
            _ <- Timer[F].sleep(retryTimeout)
            ratesMap <- ContextShift[F].shift *> performRetryRefreshCall()
          } yield ratesMap
      }
    } yield ratesMap
  }

  def nextRetryTimeout(refreshTimeout: FiniteDuration,
                       defaultTimeout: FiniteDuration)
                      (implicit cacheState: CacheState[F]): F[FiniteDuration] = {

    def getNextTimeout(currentTimeout: FiniteDuration): FiniteDuration = {
      val factor = if (currentTimeout.lt(1.second)) 5 else if (currentTimeout.lt(10.second)) 2 else 1.5
      val nextTimeout = (currentTimeout.toMillis * factor).ceil.toInt.milliseconds
      if (nextTimeout.gt(refreshTimeout)) refreshTimeout else nextTimeout
    }

    for {
      timeout <- cacheState.nextRefreshDuration.tryTake.map(_.getOrElse(defaultTimeout))
      _ <- cacheState.nextRefreshDuration.tryPut(getNextTimeout(timeout))
    } yield timeout
  }

  private def getRatesForAllPairsCall: F[Either[Error, RatesList]] = {
    val apiCall = ratesService.get(Currency.allPairs).map(_.leftMap(toCacheError))
    Concurrent.timeoutTo(
      ContextShift[F].evalOn(blockingEC)(apiCall), cacheConfig.waitTimeout, errorOnTimeout.asLeft[RatesList].pure[F]
    )
  }

  private def historyAddCall(rateHistoryCall: RateHistoryCall): F[Unit] =
    ContextShift[F].evalOn(blockingEC)(historyService.add(rateHistoryCall))

  private def historyCleanCall(nowTime: Long): F[Unit] =
    ContextShift[F].evalOn(blockingEC)(historyService.clean(nowTime))
}

object CacheProgram {
  def apply[F[_]: Concurrent: Timer: ContextShift: Logger](cacheConfig: CacheConfig,
                                                           ratesService: RatesService[F],
                                                           historyService: CallsHistoryService[F],
                                                           cacheState: CacheState[F],
                                                           blockingEC: ExecutionContextExecutor): Algebra[F] = {
    new CacheProgram(cacheConfig, ratesService, historyService, cacheState, blockingEC)
  }
}

