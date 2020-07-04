package forex.services.metric

import cats.Applicative
import forex.config.MetricConfig

import scala.annotation.tailrec
import scala.collection.mutable
import cats.implicits._

class CallsHistoryService[F[_]: Applicative](config: MetricConfig) extends Algebra[F] {

  // todo - auto expired itself, possible other implementation not only as queue
  val callsHistory: mutable.Queue[TimedCall] = mutable.Queue[TimedCall]()

  override def add(timedCall: TimedCall): F[Unit] = {
    callsHistory.enqueue(timedCall).pure[F]
  }

  override def clean(now: Long): F[Unit] = {
    def cleanOld(time: Long, callsHistory: mutable.Queue[TimedCall]): Unit = {
      val timeToClean = time - config.metricLiveTimeout.toMillis
      @tailrec
      def dequeueOldOne(queue: mutable.Queue[TimedCall]): Unit = {
        queue.headOption match {
          case Some(timedCall) if timedCall.timestamp < timeToClean =>
            queue.dequeue()
            dequeueOldOne(queue)
          case _ => ()
        }
      }
      dequeueOldOne(callsHistory)
    }
    cleanOld(now, callsHistory).pure[F]
  }
}
