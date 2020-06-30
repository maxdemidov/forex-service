package forex.programs.rates

import cats.Functor
import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.MVar
import errors._
import cats.implicits._
import forex.domain._
import forex.services.{CacheService, RatesService}

class Program[F[_]: Functor : Concurrent](
    ratesService: RatesService[F],
    cacheService: CacheService[F]
) extends Algebra[F] {

  var cacheState: F[MVar[F, Option[CacheState]]] = refresh().flatMap {
    case Right(cache) =>
      MVar.of[F, Option[CacheState]](cache)
    case Left(e) =>
      MVar.of[F, Option[CacheState]](None)
  }

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] =
    EitherT(ratesService.get(Rate.Pair(request.from, request.to), cacheState))
      .leftMap(toProgramError(_)).value

  override def refresh(): F[Error Either Option[CacheState]] = {
    val e = for {
      state <- cacheState
      r <- cacheService.refresh(state)
    } yield r
    EitherT(e).leftMap(toProgramError(_)).value
  }
}

object Program {

  def apply[F[_]: Functor : Concurrent](
      ratesService: RatesService[F],
      cacheService: CacheService[F]
  ): Algebra[F] = new Program[F](ratesService, cacheService)
}
