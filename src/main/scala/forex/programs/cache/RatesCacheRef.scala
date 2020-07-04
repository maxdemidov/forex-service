package forex.programs.cache

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import forex.domain.Rate
import forex.programs.cache.RatesCacheRef.RatesCache

case class RatesCacheRef[F[_]](ratesCache: Ref[F, RatesCache[F]])

case object RatesCacheRef {
  type RatesMap = Map[Rate.Pair, Rate]
  case class RatesCache[F[_]](cacheUUID: java.util.UUID,
                              ratesMap: Deferred[F, RatesMap],
                              toFillTrigger: Semaphore[F],
                              getsCount: Ref[F, Long],
                              status: Ref[F, CacheStatus])

  sealed trait CacheStatus
  case object EmptyCache extends CacheStatus
  case object ActiveCache extends CacheStatus
}
