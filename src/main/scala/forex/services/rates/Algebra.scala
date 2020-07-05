package forex.services.rates

import errors._
import forex.domain.Rate
import forex.domain.types.RateTypes.RatesList

trait Algebra[F[_]] {
  def get(pairs: List[Rate.Pair]): F[Error Either RatesList]
}
