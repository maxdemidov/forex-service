package forex.services.rates

import cats.effect.concurrent.MVar
import forex.domain.Rate
import errors._
import forex.programs.rates.CacheState

trait Algebra[F[_]] {
  def get(pair: Rate.Pair,
          state: F[MVar[F, Option[CacheState]]]): F[Error Either Rate]
}
