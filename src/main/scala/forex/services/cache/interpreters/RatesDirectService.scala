package forex.services.cache.interpreters

import cats.effect.{Clock, Concurrent}
import forex.domain.Rate
import forex.services.RatesService
import forex.services.cache.{Algebra, errors}
import io.chrisdavenport.log4cats.Logger

class RatesDirectService[F[_]: Concurrent: Clock: Logger](ratesService: RatesService[F]) extends Algebra[F] {

  override def get(pairs: List[Rate.Pair]): F[errors.Error Either List[Rate]] = ???
}
