package forex.programs.rates

import cats.Functor
import cats.data.EitherT
import cats.effect.{Concurrent, Timer}
import errors._
import forex.programs.rates.Protocol.GetRatesResponse
import forex.services.RatesCacheService

class Program[F[_]: Functor : Concurrent: Timer](ratesCacheService: RatesCacheService[F]) extends Algebra[F] {

  import Converters._

  override def get(request: Protocol.GetRatesRequest): F[Error Either GetRatesResponse] =
    EitherT(ratesCacheService.get(request.asPair))
      .map(_.asGetRatesResponse).leftMap(toProgramError).value

  override def get(requests: List[Protocol.GetRatesRequest]): F[Error Either List[GetRatesResponse]] =
    EitherT(ratesCacheService.get(requests.map(_.asPair)))
      .map(_.map(_.asGetRatesResponse)).leftMap(toProgramError).value
}

object Program {

  def apply[F[_]: Functor : Concurrent: Timer](ratesCacheService: RatesCacheService[F]): Algebra[F] =
    new Program[F](ratesCacheService)
}
