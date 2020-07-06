package forex.programs.rates

import cats.Functor
import cats.data.EitherT
import cats.implicits._
import cats.effect.{Concurrent, Timer}
import errors._
import forex.programs.rates.Protocol.GetRatesResponse
import forex.services.RatesCacheService

class Program[F[_]: Functor : Concurrent: Timer](ratesCacheService: RatesCacheService[F]) extends Algebra[F] {

  import Converters._

  override def get(request: Protocol.GetRatesRequest): F[Error Either GetRatesResponse] =
    EitherT(ratesCacheService.get(request.asPair))
      .leftMap(toProgramError).map(_.asGetRatesResponse).value

  override def get(requests: List[Protocol.GetRatesRequest]): F[List[Error] Either List[GetRatesResponse]] = {
    // todo - ? better
    ratesCacheService.get(requests.map(_.asPair)).map(list => {
      val lefts = list.flatMap(_.left.toOption)
      val rights = list.flatMap(_.right.toOption)
      (lefts, rights) match {
        case (Nil, rates) => rates.map(_.asGetRatesResponse).asRight[List[Error]]
        case (errors, _)  => errors.map(toProgramError).asLeft[List[GetRatesResponse]]
      }
    })
  }
}

object Program {

  def apply[F[_]: Functor : Concurrent: Timer](ratesCacheService: RatesCacheService[F]): Algebra[F] =
    new Program[F](ratesCacheService)
}
