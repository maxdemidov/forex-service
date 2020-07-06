package forex.services.history

import cats.Applicative
import forex.config.HistoryConfig
import forex.services.history.interpreters.QueueCallsHistoryService

object Interpreters {

  def queue[F[_]: Applicative](config: HistoryConfig): Algebra[F] =
    new QueueCallsHistoryService[F](config)
}
