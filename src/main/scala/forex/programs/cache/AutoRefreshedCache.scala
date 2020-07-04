package forex.programs
package cache

import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import errors._
import forex.config.CacheConfig
import forex.domain.Currency
import forex.programs.cache.RatesCacheRef.{CacheUUID, RatesCache, RatesMap}
import forex.services.{CallsHistoryService, RatesService}
import forex.programs.cache.errors.Error.{CacheRefreshOneFrameError, CacheRefreshParseResponseFailed, CacheRefreshRequestFailed, CacheRefreshTimeoutExceeded}
import forex.services.cache.{Algebra, RatesCacheService}
import forex.services.metric.TimedCall
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object AutoRefreshedCache {

  val errorOnTimeout: Error = CacheRefreshTimeoutExceeded("Cash refresh timeout exceeded")

  def initiate[F[_]: Concurrent: Timer: ContextShift: Logger](config: CacheConfig,
                                                              ratesService: RatesService[F],
                                                              historyCalls: CallsHistoryService[F],
                                                              blockingEC: ExecutionContextExecutor): F[Algebra[F]] = {

    def performEagerRefresh()(implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        cachedTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Logger[F].debug(s"perform eager refresh time = [$cachedTime]") // todo - show formatted

        ratesMap <- ContextShift[F].shift *> performRefreshCall()
        newRateCache <- RatesCache.empty
        _ <- newRateCache.ratesMap.complete(ratesMap)
        _ <- cacheRef.ratesCache.set(newRateCache)

        _ <- Concurrent[F].start(
          scheduleNextClean(config.expirationTimeout, newRateCache.cacheUUID)
        )
        _ <- stateRef.nextRetryAfter.tryTake
        _ <- scheduleNextRefresh(config.refreshTimeout)
      } yield ()
    }

    def performLazyRefresh()(implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        cachedTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Logger[F].debug(s"perform lazy refresh time = [$cachedTime]") // todo - show formatted

        newRateCache <- RatesCache.empty
        _ <- cacheRef.ratesCache.set(newRateCache)
        _ <- newRateCache.calls.acquire
        ratesMap <- ContextShift[F].shift *> performRefreshCall()
        _ <- newRateCache.ratesMap.complete(ratesMap)
        _ <- newRateCache.calls.release

        _ <- Concurrent[F].start(
          scheduleNextClean(config.expirationTimeout, newRateCache.cacheUUID)
        )
        _ <- stateRef.nextRetryAfter.tryTake
        _ <- scheduleNextRefresh(config.refreshTimeout)
      } yield ()
    }

    def performRefreshCall()(implicit stateRef: RatesStateRef[F]): F[RatesMap] = {
      val apiCall = ratesService.refresh(Currency.allPairs).map(_.leftMap(toCacheError))
      for {
        cachedTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        frameResponse <- Concurrent.timeoutTo(
          ContextShift[F].evalOn(blockingEC)(apiCall), config.waitTimeout, errorOnTimeout.asLeft[RatesMap].pure[F]
        )
        res <- frameResponse match {
          case Right(ratesMap) =>
            for {
              _ <- Concurrent[F].start(addTo(TimedCall(cachedTime, s"rates successfully obtained, map size = [${ratesMap.size}]")))
              res <- ratesMap.pure[F]
            } yield res
          case Left(error: Error) =>
            for {
              _ <- Concurrent[F].start(addTo(TimedCall(cachedTime, s"error when obtaining rates = $error")))
              res <- error match {
                case CacheRefreshTimeoutExceeded(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshTimeoutExceeded msg = " + msg)
                    timeout <- CacheUtil.nextRetryTimeout[F](config, 5.seconds)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
                case CacheRefreshParseResponseFailed(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshParseResponseFailed msg = " + msg)
                    timeout <- CacheUtil.nextRetryTimeout[F](config, 2.seconds)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
                case CacheRefreshRequestFailed(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshRequestFailed msg = " + msg)
                    timeout <- CacheUtil.nextRetryTimeout[F](config, 2.seconds)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
                case CacheRefreshOneFrameError(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshOneFrameError msg = " + msg)
                    timeout <- CacheUtil.nextRetryTimeout[F](config, config.refreshTimeout)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
              }
            } yield res
        }
      } yield res
    }

    def scheduleRetryRefresh(scheduleDuration: FiniteDuration)
                            (implicit stateRef: RatesStateRef[F]): F[RatesMap] = {
      for {
        _ <- Logger[F].debug(s"schedule next retry on error with timeout = [$scheduleDuration]")
        _ <- Timer[F].sleep(scheduleDuration)
        frameResponse <- performRefreshCall()
      } yield frameResponse
    }

    def performClean(cacheUUID: CacheUUID)
                    (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        now <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Logger[F].debug(s"perform clean on time = [$now]")
        ratesCache <- cacheRef.ratesCache.get
        _ <- if (ratesCache.cacheUUID == cacheUUID) performLazyRefresh() else ().pure[F]
        _ <- ContextShift[F].evalOn(blockingEC)(historyCalls.clean(now))
      } yield ()
    }

    def addTo(timedCall: TimedCall): F[Unit] = {
      ContextShift[F].evalOn(blockingEC)(historyCalls.add(timedCall))
    }

    def scheduleNextRefresh(scheduleDuration: FiniteDuration)
                           (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        _ <- Logger[F].debug(s"schedule next refresh with timeout = [$scheduleDuration]")
        _ <- Timer[F].sleep(scheduleDuration)
        calls <- cacheRef.ratesCache.get.flatMap(_.calls.available)
        _ <- Logger[F].debug(s"last calls = [$calls]")
        _ <- Concurrent[F].start(if (calls > 0) performEagerRefresh() else performLazyRefresh())
      } yield ()
    }

    def scheduleNextClean(scheduleDuration: FiniteDuration, cacheUUID: CacheUUID)
                         (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        _ <- Logger[F].debug(s"schedule next clean with timeout = [$scheduleDuration]")
        c <- Timer[F].sleep(scheduleDuration)
        _ <- Concurrent[F].start(performClean(cacheUUID))
      } yield c
    }

    for { // todo - use better monadic for
      cacheRef <- RatesCacheRef.initial
      stateRef <- RatesStateRef.initial
      _ <- Concurrent[F].start(performEagerRefresh()(cacheRef, stateRef)) // todo - consider to perform lazy refresh which triggered at once
    } yield new RatesCacheService[F](cacheRef)
  }
}

// todo
//  test - concurrent first call when cache is empty - they should waiting and only one call should processed to frame
//  test - if cant get rates from frame in time - call often while didn't get and if cache expired process error correctly
