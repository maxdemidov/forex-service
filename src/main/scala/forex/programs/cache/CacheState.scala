package forex.programs
package cache

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, MVar, Ref, Semaphore}
import cats.implicits._
import forex.domain.types.RateTypes.RatesMap
import forex.programs.cache.CacheState.RatesCache

import scala.concurrent.duration.FiniteDuration

case class CacheState[F[_]](ratesCache: Ref[F, RatesCache[F]],
                            nextRefreshDuration: MVar[F, FiniteDuration])

case object CacheState {

  case class RatesCache[F[_]](cacheUUID: CacheUUID,
                              ratesMap: Deferred[F, RatesMap],
                              calls: Semaphore[F])
  case object RatesCache {
    def empty[F[_]: Concurrent]: F[RatesCache[F]] = {
      for {
        newCacheUUID <- CacheUUID.random.pure[F]
        newRatesMap <- Deferred[F, RatesMap]
        newCalls <- Semaphore[F](0)
        ratesCache <- RatesCache(newCacheUUID, newRatesMap, newCalls).pure[F]
      } yield ratesCache
    }
  }

  case class CacheUUID(uuid: java.util.UUID) extends AnyVal
  case object CacheUUID {
    def random: CacheUUID =
      CacheUUID(java.util.UUID.randomUUID())
  }

  def initial[F[_]: Concurrent]: F[CacheState[F]] = {
    for {
      newRateCache <- RatesCache.empty
      ratesCacheRef <- Ref[F].of(newRateCache)
      nextRefreshDuration <- MVar[F].empty[FiniteDuration]
      cache <- CacheState(ratesCacheRef, nextRefreshDuration).pure[F]
    } yield cache
  }
}
