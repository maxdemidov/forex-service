package forex.services.cache

import cats.Applicative
import cats.effect.Concurrent
import forex.services.cache.interpreters.OneFrameCache

object Interpreters {
  def live[F[_]: Applicative: Concurrent](): Algebra[F] = new OneFrameCache[F]()
}
