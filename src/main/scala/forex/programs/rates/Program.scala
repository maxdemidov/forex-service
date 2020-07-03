package forex.programs.rates

import cats.Functor
import cats.data.EitherT
import cats.effect.{Concurrent, Timer}
import errors._
import forex.domain._
import forex.services.StateService

class Program[F[_]: Functor : Concurrent: Timer](stateService: StateService[F]) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] =
    EitherT(stateService.get(Rate.Pair(request.from, request.to))).leftMap(toProgramError).value
}

object Program {

  def apply[F[_]: Functor : Concurrent: Timer](
      stateService: StateService[F]
  ): Algebra[F] = new Program[F](stateService)
}
