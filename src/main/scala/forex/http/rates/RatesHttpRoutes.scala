package forex.http
package rates

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.flatMap._
import forex.domain.Currency
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error.RateLookupFailed
import io.chrisdavenport.log4cats.Logger
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync: Logger](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {

    case OPTIONS -> Root / "currencies" =>
      Ok(Currency.allCurrencies)

    case GET -> Root / "rate" :? FromQueryParam(fromE) +& ToQueryParam(toE) =>
      val params = for {
        from <- EitherT.fromEither(fromE)
        to <- EitherT.fromEither(toE)
      } yield GetApiRequest(from, to)
      params.value.flatMap {
        case Right(request) => getRate(request)
        case Left(msg)      => Logger[F].warn(msg).flatMap(_ => BadRequest(msg))
      }

    case GET -> Root :? PairQueryParam(pairsE) =>
      pairsE.toEither match {
        case Right(list) =>
          val lefts = list.flatMap(_.left.toOption)
          val rights = list.flatMap(_.right.toOption)
          (lefts, rights) match {
            case (Nil, pairs) => getRates(pairs.distinct)
            case (errors, _)  => BadRequest(errors.reduce(_ + "\n\r" + _))
          }
        case Left(nel) => BadRequest(nel.map(_.message).toList.reduce(_ + "\n\r" + _))
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

  // todo - make one for list
  private def getRate(request: GetApiRequest): F[Response[F]] = {
    rates.get(request.asGetRatesRequest).flatMap {
      case Right(rate)                 => Ok(rate.asGetApiResponse)
      case Left(RateLookupFailed(msg)) => InternalServerError(msg)
    }
  }

  private def getRates(pairs: List[GetApiRequest]): F[Response[F]] = {
    rates.get(pairs.map(_.asGetRatesRequest)).flatMap {
      case Right(rates)  => Ok(rates.map(_.asGetApiResponse))
      case Left(errors)  => InternalServerError(errors.map(_.toString).reduce(_ + "\n\r" + _))
    }
  }
}
