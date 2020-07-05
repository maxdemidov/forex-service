package forex.services.cache

import forex.domain.Rate
import forex.services.cache.errors.Error

trait Algebra[F[_]] {
  def get(pair: Rate.Pair): F[Error Either Rate]
  def get(pairs: List[Rate.Pair]): F[List[Error Either Rate]]
}
