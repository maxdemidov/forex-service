package forex.services.history

import cats.Applicative
import forex.config.HistoryConfig

object Interpreters {

  def queue[F[_]: Applicative](config: HistoryConfig): Algebra[F] =
    new CallsHistoryService[F](config)
}
