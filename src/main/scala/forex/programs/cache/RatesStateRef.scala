package forex.programs.cache

import cats.effect.concurrent.MVar
import forex.programs.cache.RatesStateRef.FrameTimedCall

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

case class RatesStateRef[F[_]](nextRetryAfter: MVar[F, FiniteDuration],
                               callsLast24Hours: mutable.Queue[FrameTimedCall])

case object RatesStateRef {
  case class FrameTimedCall(timestamp: Long, response: String)
}