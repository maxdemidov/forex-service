package forex.services.metric

import cats.Applicative
import forex.config.MetricConfig

object Interpreters {

  def queue[F[_]: Applicative](config: MetricConfig): Algebra[F] =
    new CallsHistoryService[F](config)
}
