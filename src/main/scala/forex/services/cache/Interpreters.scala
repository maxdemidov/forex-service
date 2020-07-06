package forex.services.cache

import cats.effect.{Clock, Concurrent}
import forex.programs.CacheProgram
import forex.services.cache.interpreters.RatesCacheService
import io.chrisdavenport.log4cats.Logger

object Interpreters {

  def live[F[_]: Concurrent: Clock: Logger](cacheProgram: CacheProgram[F]): Algebra[F] =
    new RatesCacheService[F](cacheProgram)
}
