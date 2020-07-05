package forex.services.rates.interpreters

import cats.Applicative
import cats.effect.{Async, Concurrent}
import cats.implicits._
import forex.common.parser.CommonJsonParser
import forex.config.FrameConfig
import forex.domain.RateTypes.RatesList
import forex.domain.{Currency, Rate}
import forex.services.rates.errors.Error.{OneFrameError, ParseResponseFailed, RequestFailed}
import forex.services.rates.frame.Protocol.{FrameError, FrameRate}
import forex.services.rates.errors._
import forex.services.rates.Algebra
import io.chrisdavenport.log4cats.Logger
import scalaj.http.Http

import scala.util.{Failure, Success, Try}

class OneFrameLive[F[_]: Applicative: Concurrent: Logger](config: FrameConfig) extends Algebra[F]
    with CommonJsonParser {

  val url = s"http://${config.host}:${config.port}"

  override def get(pairs: List[Rate.Pair]): F[Either[Error, RatesList]] = {
    for {
      _ <- Logger[F].debug(s"Call one frame to obtain rates for pairs = [$pairs]")
      res <- Async[F].async[Either[Error, RatesList]] { cb =>
        allRatesRequest() match {
          case Right(allRates) =>
            cb(allRates.asRight[Error].asRight[Throwable])
          case Left(error) =>
            cb(error.asLeft[RatesList].asRight[Throwable])
        }
      }
    } yield res
  }

  private def allRatesRequest(): Either[Error, RatesList] = {
    import forex.services.rates.frame.Converters._
    val allPairs = Currency.allPairs.map(p => s"pair=${p.from}${p.to}").reduce(_ + "&" + _)
    val ratesUrl = s"$url/rates?$allPairs"
    val token = config.token
    Try(Http(ratesUrl).header("token", token).asString) match {
      case Success(response) =>
        parseTo[List[FrameRate]](response.body) match {
          case Right(frameRates) =>
            frameRates.map(_.asRate).asRight[Error]
          case Left(_) =>
            parseTo[FrameError](response.body) match {
              case Right(frameError) => OneFrameError(frameError.error.message).asLeft[RatesList]
              case Left(msg) =>         ParseResponseFailed(msg).asLeft[RatesList]
            }
        }
      case Failure(e) =>
        RequestFailed(e.getMessage).asLeft[RatesList]
    }
  }
}
