package forex.programs
package cache

import cats.effect.Concurrent
import cats.effect.concurrent.MVar
import cats.implicits._
import forex.programs.cache.RatesStateRef.FrameTimedCall

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

case class RatesStateRef[F[_]](nextRetryAfter: MVar[F, FiniteDuration],
                               callsHistory: mutable.Queue[FrameTimedCall]) // todo - algebra with put and auto expired itself

case object RatesStateRef {
  case class FrameTimedCall(timestamp: Long, response: String) // todo - Timestamp

  private[cache] def initial[F[_]: Concurrent]: F[RatesStateRef[F]] = {
    for {
      nextRefreshDuration <- MVar[F].empty[FiniteDuration]
      callsHistory <- mutable.Queue[FrameTimedCall]().pure[F]
      state <- RatesStateRef(nextRefreshDuration, callsHistory).pure[F]
    } yield state
  }
}