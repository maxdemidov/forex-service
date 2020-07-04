package forex.programs
package cache

import cats.effect.Concurrent
import cats.effect.concurrent.MVar
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

case class RatesStateRef[F[_]](nextRetryAfter: MVar[F, FiniteDuration])

case object RatesStateRef {
  private[cache] def initial[F[_]: Concurrent]: F[RatesStateRef[F]] = {
    for {
      nextRefreshDuration <- MVar[F].empty[FiniteDuration]
      state <- RatesStateRef(nextRefreshDuration).pure[F]
    } yield state
  }
}