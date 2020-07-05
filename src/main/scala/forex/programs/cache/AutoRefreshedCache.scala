package forex.programs
package cache

import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import errors._
import forex.common.datetime.DateTimeConverters
import forex.config.CacheConfig
import forex.domain.Currency
import forex.domain.types.RateTypes.RatesList
import forex.programs.cache.RatesCacheRef.{CacheUUID, RatesCache}
import forex.services.{CallsHistoryService, RatesService}
import forex.programs.cache.errors.Error.CacheRefreshTimeoutExceeded
import forex.services.cache.{Algebra, RatesCacheService}
import forex.services.history.RateHistoryCall
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

object AutoRefreshedCache {

  val errorOnTimeout: Error = CacheRefreshTimeoutExceeded("Cash refresh timeout exceeded")

  def initiate[F[_]: Concurrent: Timer: ContextShift: Logger](cacheConfig: CacheConfig,
                                                              ratesService: RatesService[F],
                                                              historyCalls: CallsHistoryService[F],
                                                              blockingEC: ExecutionContextExecutor): F[Algebra[F]] = {

    implicit val refreshTimeout: FiniteDuration = cacheConfig.refreshTimeout

    def getRatesForAllPairsCall: F[Either[Error, RatesList]] = {
      val apiCall = ratesService.get(Currency.allPairs).map(_.leftMap(toCacheError))
      Concurrent.timeoutTo(
        ContextShift[F].evalOn(blockingEC)(apiCall), cacheConfig.waitTimeout, errorOnTimeout.asLeft[RatesList].pure[F]
      )
    }

    def historyAddCall(rateHistoryCall: RateHistoryCall): F[Unit] =
      ContextShift[F].evalOn(blockingEC)(historyCalls.add(rateHistoryCall))

    def historyCleanCall(nowTime: Long): F[Unit] =
      ContextShift[F].evalOn(blockingEC)(historyCalls.clean(nowTime))

    def performRefresh(isEager: Boolean)
                      (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] =
      for {
        refreshTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
        _ <- Logger[F].debug(s"Perform refresh, isEager = [$isEager], time = [$refreshTime]")
        newRateCache <- if (isEager) {
          for {
            ratesMap <- ContextShift[F].shift *> AutoRetryCall.performRetryRefreshCall(getRatesForAllPairsCall, historyAddCall)
            newRateCache <- RatesCache.empty
            _ <- newRateCache.ratesMap.complete(ratesMap)
            _ <- showLastCount() *> cacheRef.ratesCache.set(newRateCache)
          } yield newRateCache
        } else {
          for {
            newRateCache <- RatesCache.empty
            _ <- showLastCount() *> cacheRef.ratesCache.set(newRateCache)
            _ <- newRateCache.calls.acquire
            ratesMap <- ContextShift[F].shift *> AutoRetryCall.performRetryRefreshCall(getRatesForAllPairsCall, historyAddCall)
            _ <- newRateCache.ratesMap.complete(ratesMap)
            _ <- newRateCache.calls.release
          } yield newRateCache
        }
        _ <- ContextShift[F].shift *> scheduleNextClean(cacheConfig.expirationTimeout, newRateCache.cacheUUID)
        _ <- stateRef.nextRetryAfter.tryTake
        _ <- ContextShift[F].shift *> scheduleNextRefresh(cacheConfig.refreshTimeout)
      } yield ()

    def showLastCount()(implicit cacheRef: RatesCacheRef[F]): F[Unit] = {
      for {
        callsCount <- cacheRef.ratesCache.get.flatMap(_.calls.available)
        _ <- Logger[F].debug(s"Last calls to previous cache, count = [$callsCount]")
      } yield ()
    }

    def scheduleNextRefresh(scheduleDuration: FiniteDuration)
                           (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] =
      for {
        _ <- Logger[F].debug(s"Schedule next refresh with timeout = [$scheduleDuration]")
        _ <- Timer[F].sleep(scheduleDuration)
        isAnyCalls <- cacheRef.ratesCache.get.flatMap(_.calls.available.map(_ > 0))
        _ <- Concurrent[F].start(
          if (isAnyCalls) performRefresh(isEager = true) else performRefresh(isEager = false)
        )
      } yield ()

    def scheduleNextClean(scheduleDuration: FiniteDuration, cacheUUID: CacheUUID)
                         (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] =
      for {
        _ <- Logger[F].debug(s"Schedule next clean with timeout = [$scheduleDuration]")
        _ <- Timer[F].sleep(scheduleDuration)
        ratesCache <- cacheRef.ratesCache.get
        _ <- if (ratesCache.cacheUUID == cacheUUID) {
          for {
            _ <- Concurrent[F].start(performRefresh(isEager = false)).pure[F]
          } yield ()
        } else ().pure[F]
        nowTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Concurrent[F].start(historyCleanCall(nowTime))
      } yield ()

    for { // todo - use better monadic for
      cacheRef <- RatesCacheRef.initial
      stateRef <- RatesStateRef.initial
      _ <- Concurrent[F].start(performRefresh(isEager = true)(cacheRef, stateRef))
    } yield new RatesCacheService[F](cacheRef) // todo - get instance from outside like other services - initialize on override constructor
  }
}

// todo
//  test - concurrent first call when cache is empty - they should waiting and only one call should processed to frame
//  test - if cant get rates from frame in time - call often while didn't get and if cache expired process error correctly
