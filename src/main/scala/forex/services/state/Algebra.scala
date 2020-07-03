package forex.services.state

import forex.domain.Rate
import forex.services.state.errors.Error

trait Algebra[F[_]] {
  def get(pair: Rate.Pair): F[Error Either Rate]
}
