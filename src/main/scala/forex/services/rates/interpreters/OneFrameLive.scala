package forex.services.rates.interpreters

import cats.Applicative
import cats.effect.{Async, Concurrent}
import cats.implicits._
import forex.common.parser.CommonJsonParser
import forex.config.FrameConfig
import forex.domain.{Currency, Rate}
import forex.programs.cache.RatesCacheRef.RatesMap
import forex.services.rates.errors.Error.{OneFrameError, ParseResponseFailed, RequestFailed}
import forex.services.rates.frame.Protocol.{FrameError, FrameRate}
import forex.services.rates.errors._
import forex.services.rates.Algebra
import io.chrisdavenport.log4cats.Logger
import scalaj.http.Http

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class OneFrameLive[F[_]: Applicative: Concurrent: Logger](config: FrameConfig) extends Algebra[F]
    with CommonJsonParser {

  val url = s"http://${config.host}:${config.port}"

  val emptyRatesMap: RatesMap = Map[Rate.Pair, Rate]()

  override def refresh(pairs: List[Rate.Pair]): F[Either[Error, RatesMap]] = {
    for {
      _ <- Logger[F].info("call external service")
      res <- Async[F].async[Either[Error, RatesMap]] { cb =>
        allRatesResponse() match {
          case Right(allRates) =>
            val mapByFrom = getMapByPair(allRates)
            cb(mapByFrom.asRight[Error].asRight[Throwable])
          case Left(error) =>
            cb(error.asLeft[RatesMap].asRight[Throwable])
        }
      }
    } yield res
  }

  private def allRatesResponse(): Either[Error, List[FrameRate]] = { // todo - consider to use optionT
    val allPairs = Currency.allPairs.map(p => s"pair=${p.from}${p.to}").reduce(_ + "&" + _)
    val ratesUrl = s"$url/rates?$allPairs"
    val token = config.token
    Try(Http(ratesUrl).header("token", token).asString) match {
      case Success(response) =>
        parseTo[List[FrameRate]](response.body) match {
          case Right(frameRates) => frameRates.asRight[Error]
          case Left(_) =>
            parseTo[FrameError](response.body) match {
              case Right(frameError) =>
                OneFrameError(frameError.error.message).asLeft[List[FrameRate]]
              case Left(msg) =>
                ParseResponseFailed(msg).asLeft[List[FrameRate]]
            }
        }
      case Failure(e) =>
        RequestFailed(e.getMessage).asLeft[List[FrameRate]]
    }
  }

  private def getMapByPair(allRates: List[FrameRate]): RatesMap = {
    import forex.services.rates.frame.Converters._
    @tailrec
    def convertToMap(frameRates: List[FrameRate], map: RatesMap): RatesMap = {
      frameRates match {
        case frameRate :: otherFrameRates =>
          val pair = Rate.Pair(frameRate.from, frameRate.to)
          val rate = frameRate.asRate
          convertToMap(otherFrameRates, map + (pair -> rate))
        case Nil => map
      }
    }
    convertToMap(allRates, emptyRatesMap)
  }
}
