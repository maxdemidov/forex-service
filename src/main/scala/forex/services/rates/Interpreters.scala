package forex.services.rates

import cats.Applicative
import cats.effect.Concurrent
import forex.config.FrameConfig
import interpreters._
import io.chrisdavenport.log4cats.Logger

object Interpreters {

  def live[F[_]: Applicative: Concurrent: Logger](config: FrameConfig): Algebra[F] =
    new OneFrameLive[F](config)
}
