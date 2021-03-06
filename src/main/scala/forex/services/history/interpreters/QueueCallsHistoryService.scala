package forex.services.history.interpreters

import cats.Applicative
import cats.implicits._
import forex.config.HistoryConfig
import forex.services.history.Algebra
import forex.services.history.model.RateHistoryCall

import scala.annotation.tailrec
import scala.collection.mutable

class QueueCallsHistoryService[F[_]: Applicative](config: HistoryConfig) extends Algebra[F] {

  // todo - auto expired itself, possible other implementation not only as queue
  val callsHistory: mutable.Queue[RateHistoryCall] = mutable.Queue[RateHistoryCall]()

  override def add(rateHistoryCall: RateHistoryCall): F[Unit] = {
    callsHistory.enqueue(rateHistoryCall).pure[F]
  }

  override def clean(now: Long): F[Unit] = {
    def cleanOld(time: Long, callsHistory: mutable.Queue[RateHistoryCall]): Unit = {
      val timeToClean = time - config.historyLiveTimeout.toMillis
      @tailrec
      def dequeueOldOne(queue: mutable.Queue[RateHistoryCall]): Unit = {
        queue.headOption match {
          case Some(rateHistoryCall) if rateHistoryCall.callTime < timeToClean =>
            queue.dequeue()
            dequeueOldOne(queue)
          case _ => ()
        }
      }
      dequeueOldOne(callsHistory)
    }
    cleanOld(now, callsHistory).pure[F]
  }

  override def getAll: F[List[RateHistoryCall]] = {
    callsHistory.toList.pure[F]
  }
}
