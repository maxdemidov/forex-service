package forex.services.rates

import errors._
import forex.domain.Rate
import forex.programs.cache.RatesCacheRef.RatesMap

trait Algebra[F[_]] {
  def refresh(pairs: List[Rate.Pair]): F[Error Either RatesMap]
}
