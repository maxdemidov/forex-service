package forex.http
package rates

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.flatMap._
import forex.domain.Currency
import forex.programs.RatesProgram
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root :? FromQueryParam(fromE) +& ToQueryParam(toE) =>

      val params = for {
        from <- EitherT.fromEither(fromE)
        to <- EitherT.fromEither(toE)
      } yield (from, to)

      params.value.flatMap {
        case Right((from, to)) => getRates(from, to)
        case Left(msg) => BadRequest(msg)
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

  private def getRates(from: Currency, to: Currency): F[Response[F]] = {
    rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap(Sync[F].fromEither).flatMap { rate =>
      Ok(rate.asGetApiResponse)
    }
  }
}
