package forex.services.cache

import cats.effect.concurrent.MVar
import forex.programs.rates.CacheState
import forex.services.rates.errors.Error

trait Algebra[F[_]] {
  def refresh(state: MVar[F, Option[CacheState]]): F[Error Either Option[CacheState]]
}
