package forex.services.rates

import cats.Applicative
import cats.effect.Concurrent
import interpreters._

object Interpreters {
  def dummy[F[_]: Applicative](): Algebra[F] = new OneFrameDummy[F]()
  def live[F[_]: Applicative: Concurrent](): Algebra[F] = new OneFrameLive[F]()
}
