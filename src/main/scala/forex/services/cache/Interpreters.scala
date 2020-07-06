package forex.services.cache

import cats.effect.{Clock, Concurrent}
import forex.programs.CacheProgram
import forex.services.RatesService
import forex.services.cache.interpreters.{RatesCacheService, RatesDirectService}
import io.chrisdavenport.log4cats.Logger

object Interpreters {

  def cached[F[_]: Concurrent: Clock: Logger](cacheProgram: CacheProgram[F]): Algebra[F] =
    new RatesCacheService[F](cacheProgram)

  def direct[F[_]: Concurrent: Clock: Logger](ratesService: RatesService[F]): Algebra[F] =
    new RatesDirectService[F](ratesService)
}
