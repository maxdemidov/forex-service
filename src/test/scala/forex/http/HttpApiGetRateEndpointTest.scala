package forex.http

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Timer}
import forex.helper.HttpRouteTestHelper.RatesCacheServiceEmpty
import forex.common.parser.CommonJsonParser
import forex.domain.{Ask, Bid, Currency, Price, Rate, Timestamp}
import forex.helper.HttpRouteTestHelper
import forex.helper.HttpRouteTestHelper.RatesCacheServiceOne
import org.http4s._
import org.http4s.implicits._
import forex.http.rates.Protocol.GetApiResponse
import forex.http.rates.QueryParams
import forex.services.cache.errors.Messages
import fs2.text.utf8Decode
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.{Method, Request}
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class HttpApiGetRateEndpointTest extends AnyFunSuite with CommonJsonParser {

  import forex.programs.rates.Converters._
  import forex.http.rates.Converters._

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val mainEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(mainEC)
  implicit def timer: Timer[IO] = IO.timer(mainEC)

  implicit val responseDecoder: Decoder[GetApiResponse] = deriveDecoder[GetApiResponse]

  val pairUSDEUR: Rate.Pair = Rate.Pair(Currency.USD, Currency.EUR)
  val rateUSDEUR: Rate =
    Rate(pairUSDEUR, Bid(BigDecimal(0.9)), Ask(BigDecimal(0.5)), Price(BigDecimal(0.7)), Timestamp.now)

  val pairUSDJPY: Rate.Pair = Rate.Pair(Currency.USD, Currency.JPY)
  val rateUSDJPY: Rate =
    Rate(pairUSDJPY, Bid(BigDecimal(0.9)), Ask(BigDecimal(0.5)), Price(BigDecimal(0)), Timestamp.now)

  test("get rate - successful case") {
    val uriStr = s"/rates/rate?from=${pairUSDEUR.from}&to=${pairUSDEUR.to}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceOne[IO](rateUSDEUR))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      json <- resp.body.through(utf8Decode).compile.string
      rate <- IO.apply(parseTo[GetApiResponse](json))
    } yield (rate, status)
    val (rateE, status) = rateIO.unsafeRunSync()

    rateE match {
      case Right(rate) => assert(rate == rateUSDEUR.asGetRatesResponse.asGetApiResponse)
      case Left(msg) => throw HttpRouteTestHelper.unsatisfiedResponse(msg)
    }
    assert(status == Status.Ok)
  }

  test("get rate - not found case") {
    val uriStr = s"/rates/rate?from=${pairUSDJPY.from}&to=${pairUSDJPY.to}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceOne[IO](rateUSDEUR))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)
    val (notFountMsg, status) = rateIO.unsafeRunSync()

    assert(notFountMsg == Messages.notFoundRateMessage(pairUSDJPY))
    assert(status == Status.NotFound)
  }

  test("get rate - invalid data case") {
    val uriStr = s"/rates/rate?from=${pairUSDJPY.from}&to=${pairUSDJPY.to}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceOne[IO](rateUSDJPY))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)
    val (notFountMsg, status) = rateIO.unsafeRunSync()

    assert(notFountMsg == Messages.invalidRateMessage(pairUSDJPY, rateUSDJPY))
    assert(status == Status.InternalServerError)
  }

  test("get rate - bad request in case of incorrect from or to") {
    val incorrectCurrency = "QQQ"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceEmpty[IO]())
    def rateIO(uriStr: String) = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)

    val uriStrWithIncorrectFrom = s"/rates/rate?from=$incorrectCurrency&to=${pairUSDEUR.to}"
    val (errorMsgFrom, statusFrom) = rateIO(uriStrWithIncorrectFrom).unsafeRunSync()
    assert(errorMsgFrom == QueryParams.Messages.invalidCurrency(incorrectCurrency))
    assert(statusFrom == Status.BadRequest)

    val uriStrWithIncorrectTo = s"/rates/rate?from=${pairUSDEUR.from}&to=$incorrectCurrency"
    val (errorMsgTo, statusTo) = rateIO(uriStrWithIncorrectTo).unsafeRunSync()
    assert(errorMsgTo == QueryParams.Messages.invalidCurrency(incorrectCurrency))
    assert(statusTo == Status.BadRequest)
  }

  test("get rate - not found if from or to missed") {
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceEmpty[IO]())
    def rateIO(uriStr: String) = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
    } yield status

    val uriStrWithIncorrectFrom = s"/rates/rate?to=${pairUSDEUR.to}"
    val statusFrom = rateIO(uriStrWithIncorrectFrom).unsafeRunSync()
    assert(statusFrom == Status.NotFound)

    val uriStrWithIncorrectTo = s"/rates/rate?from=${pairUSDEUR.from}"
    val statusTo = rateIO(uriStrWithIncorrectTo).unsafeRunSync()
    assert(statusTo == Status.NotFound)
  }
}
