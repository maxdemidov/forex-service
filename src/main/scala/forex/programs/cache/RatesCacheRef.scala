package forex.programs
package cache

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.implicits._
import forex.domain.Rate
import forex.programs.cache.RatesCacheRef.RatesCache

case class RatesCacheRef[F[_]](ratesCache: Ref[F, RatesCache[F]])

case object RatesCacheRef {

  type RatesMap = Map[Rate.Pair, Rate]

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

  private[cache] def initial[F[_]: Concurrent]: F[RatesCacheRef[F]] = {
    for {
      newRateCache <- RatesCache.empty
      ratesCacheRef <- Ref[F].of(newRateCache)
      cache <- RatesCacheRef(ratesCacheRef).pure[F]
    } yield cache
  }
}
