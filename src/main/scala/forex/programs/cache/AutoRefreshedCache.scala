package forex.programs.cache

import java.util.concurrent.TimeUnit

import cats.effect.concurrent.{Deferred, MVar, Ref, Semaphore}
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import errors._
import forex.config.CacheConfig
import forex.domain.Currency
import forex.programs.cache.RatesStateRef.FrameTimedCall
import forex.programs.cache.RatesCacheRef.{ActiveCache, CacheStatus, EmptyCache, RatesCache, RatesMap}
import forex.services.RatesService
import forex.programs.cache.errors.Error.{CacheRefreshOneFrameError, CacheRefreshParseResponseFailed, CacheRefreshRequestFailed, CacheRefreshTimeoutExceeded}
import forex.services.state.Algebra
import forex.services.state.interpreters.OneStateService
import io.chrisdavenport.log4cats.Logger

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object AutoRefreshedCache {

  val errorOnTimeout: Error = CacheRefreshTimeoutExceeded("Cash refresh timeout exceeded")

  def initiate[F[_]: Concurrent: Timer: ContextShift : Logger](config: CacheConfig,
                                                               ratesService: RatesService[F],
                                                               blockingEC: ExecutionContextExecutor): F[Algebra[F]] = {

    def performEagerRefresh()(implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        cachedTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Logger[F].debug(s"perform eager refresh newCachedTime = [$cachedTime]")

        ratesMap <- ContextShift[F].shift *> performRefreshCall()

        newCacheUUID <- java.util.UUID.randomUUID().pure[F]
        _ <- Concurrent[F].start(
          scheduleNextClean(config.expirationTimeout, newCacheUUID)
        )
        newGetsCount <- Ref[F].of[Long](0)
        newRatesMap <- Deferred[F, RatesMap]
        _ <- newRatesMap.complete(ratesMap)
        nawToFillTrigger <- Semaphore[F](0)
        newCacheStatus <- Ref[F].of[CacheStatus](ActiveCache)
        _ <- cacheRef.ratesCache.set(
          RatesCache(newCacheUUID, newRatesMap, nawToFillTrigger, newGetsCount, newCacheStatus)
        )
        _ <- stateRef.nextRetryAfter.tryTake
        _ <- scheduleNextRefresh(config.refreshTimeout)
      } yield ()
    }

    def performLazyRefresh()(implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        cachedTime <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Logger[F].debug(s"perform lazy refresh cachedTime = [$cachedTime]")
        cacheUUID <- java.util.UUID.randomUUID().pure[F]
        getsCount <- Ref[F].of[Long](0)
        ratesMap <- Deferred[F, RatesMap]
        toFillTrigger <- Semaphore[F](0)
        cacheStatus <- Ref[F].of[CacheStatus](EmptyCache)
        _ <- cacheRef.ratesCache.set(
          RatesCache(cacheUUID, ratesMap, toFillTrigger, getsCount, cacheStatus)
        )

        _ <- toFillTrigger.acquire
        ratesMap <- ContextShift[F].shift *> performRefreshCall()

        _ <- Concurrent[F].start(
          scheduleNextClean(config.expirationTimeout, cacheUUID)
        )
        _ <- cacheRef.ratesCache.get.flatMap(_.ratesMap.complete(ratesMap))
        _ <- cacheRef.ratesCache.get.flatMap(_.status.set(ActiveCache))
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
              _ <- Concurrent[F].start(
                addTo(FrameTimedCall(cachedTime, s"rates successfully obtained count = [${ratesMap.size}]"), stateRef.callsLast24Hours)
              )
              res <- ratesMap.pure[F]
            } yield res
          case Left(error: Error) =>
            for {
              _ <- Concurrent[F].start(
                addTo(FrameTimedCall(cachedTime, s"error when obtaining rates = ${error}"), stateRef.callsLast24Hours)
              )
              res <- error match {
                case CacheRefreshTimeoutExceeded(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshTimeoutExceeded msg = " + msg)
                    timeout <- nextRetryTimeoutOrElse(5.seconds)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
                case CacheRefreshParseResponseFailed(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshParseResponseFailed msg = " + msg)
                    timeout <- nextRetryTimeoutOrElse(2.seconds)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
                case CacheRefreshRequestFailed(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshRequestFailed msg = " + msg)
                    timeout <- nextRetryTimeoutOrElse(2.seconds)
                    res <- scheduleRetryRefresh(timeout)
                  } yield res
                case CacheRefreshOneFrameError(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshOneFrameError msg = " + msg)
                    timeout <- nextRetryTimeoutOrElse(config.refreshTimeout)
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

    def performClean(cacheUUID: java.util.UUID)
                    (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        time <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
        _ <- Logger[F].debug(s"perform clean on time = [$time]")
        ratesCache <- cacheRef.ratesCache.get
        _ <- if (ratesCache.cacheUUID == cacheUUID) performLazyRefresh() else ().pure[F]
        _ <- ContextShift[F].shift *> performCleanQueue(time, stateRef.callsLast24Hours)
      } yield ()
    }

    // todo - move queue out from cache as service with own algebra
    def performCleanQueue(time: Long,
                          callsLast24Hours: mutable.Queue[FrameTimedCall]): F[Unit] = {

      def cleanOlderThen24H(time: Long, callsLast24Hours: mutable.Queue[FrameTimedCall]): Unit = {
        val timeToClean = time - FiniteDuration(24, TimeUnit.HOURS).toMillis
        @tailrec
        def dequeueOldOne(queue: mutable.Queue[FrameTimedCall]): Unit = {
          queue.headOption match {
            case Some(timedCall) if timedCall.timestamp < timeToClean =>
              queue.dequeue()
              dequeueOldOne(queue)
            case _ => ()
          }
        }
        dequeueOldOne(callsLast24Hours)
      }

      ContextShift[F].evalOn(blockingEC)(
        cleanOlderThen24H(time, callsLast24Hours).pure[F]
      )
    }

    def addTo(item: FrameTimedCall, callsLast24Hours: mutable.Queue[FrameTimedCall]): F[Unit] = {
      ContextShift[F].evalOn(blockingEC)(
        callsLast24Hours.enqueue(item).pure[F]
      )
    }

    def scheduleNextRefresh(scheduleDuration: FiniteDuration)
                           (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        _ <- Logger[F].debug(s"schedule next refresh with timeout = [$scheduleDuration]")
        _ <- Timer[F].sleep(scheduleDuration)
        getsCount <- cacheRef.ratesCache.get.flatMap(_.getsCount.get)
        _ <- Logger[F].debug(s"last gets = [$getsCount]")
        _ <- if (getsCount > 0)
          Concurrent[F].start(performEagerRefresh())
        else
          Concurrent[F].start(performLazyRefresh())
      } yield ()
    }

    def scheduleNextClean(scheduleDuration: FiniteDuration, cacheUUID: java.util.UUID)
                         (implicit cacheRef: RatesCacheRef[F], stateRef: RatesStateRef[F]): F[Unit] = {
      for {
        _ <- Logger[F].debug(s"schedule next clean with timeout = [$scheduleDuration]")
        c <- Timer[F].sleep(scheduleDuration)
        _ <- Concurrent[F].start(performClean(cacheUUID))
      } yield c
    }

    def nextRetryTimeoutOrElse(defaultTimeout: FiniteDuration)(implicit stateRef: RatesStateRef[F]) = {

      def getNextTimeout(currentTimeout: FiniteDuration): FiniteDuration = {
        val factor = if (currentTimeout.lt(1.second)) 5 else if (currentTimeout.lt(10.second)) 2 else 1.5
        val nextTimeout = (currentTimeout.toMillis * factor).ceil.toInt.milliseconds
        if (nextTimeout.gt(config.refreshTimeout)) config.refreshTimeout else nextTimeout
      }

      for {
        timeout <- stateRef.nextRetryAfter.tryTake.map(_.getOrElse(defaultTimeout))
        nextTimeout <- getNextTimeout(timeout).pure[F]
        _ <- stateRef.nextRetryAfter.tryPut(nextTimeout)
      } yield timeout
    }

    for { // todo - use better monadic for
      cacheRef <- getInitialCacheRef
      stateRef <- getInitialStateRef
      _ <- Concurrent[F].start(performEagerRefresh()(cacheRef, stateRef)) // todo - perform lazy refresh which triggered at once
    } yield new OneStateService[F](cacheRef)
  }

  private def getInitialCacheRef[F[_]: Concurrent: Timer]: F[RatesCacheRef[F]] = {
    for {
      cacheUUID <- java.util.UUID.randomUUID().pure[F]
      getsCount <- Ref[F].of[Long](0)
      ratesMap <- Deferred[F, RatesMap]
      toFillTrigger <- Semaphore[F](0)
      cacheStatus <- Ref[F].of[CacheStatus](EmptyCache)
      ratesCacheRef <- Ref[F].of(
        RatesCache(cacheUUID, ratesMap, toFillTrigger, getsCount, cacheStatus)
      )
      cache <- RatesCacheRef(ratesCacheRef).pure[F]
    } yield cache
  }

  private def getInitialStateRef[F[_]: Concurrent]: F[RatesStateRef[F]] = {
    for {
      nextRefreshDuration <- MVar[F].empty[FiniteDuration]
      callsLast24Hours <- mutable.Queue[FrameTimedCall]().pure[F]
      state <- RatesStateRef(nextRefreshDuration, callsLast24Hours).pure[F]
    } yield state
  }
}
