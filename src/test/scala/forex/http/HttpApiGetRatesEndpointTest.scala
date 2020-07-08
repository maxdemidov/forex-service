package forex.http

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Timer}
import forex.http.rates.QueryParams
import forex.common.parser.CommonJsonParser
import forex.domain._
import forex.helper.HttpRouteTestHelper
import forex.helper.HttpRouteTestHelper.RatesCacheServiceList
import forex.http.rates.Protocol.GetApiResponse
import forex.services.cache.errors.Messages
import fs2.text.utf8Decode
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.implicits._
import org.http4s.{Method, Request, _}
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class HttpApiGetRatesEndpointTest extends AnyFunSuite with CommonJsonParser {

  import forex.http.rates.Converters._
  import forex.programs.rates.Converters._
  import HttpRouteTestHelper._

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val mainEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(mainEC)
  implicit def timer: Timer[IO] = IO.timer(mainEC)

  implicit val responseDecoder: Decoder[GetApiResponse] = deriveDecoder[GetApiResponse]

  val pairUSDEUR: Rate.Pair = Rate.Pair(Currency.USD, Currency.EUR)
  val rateUSDEUR: Rate =
    Rate(pairUSDEUR, Bid(BigDecimal(0.9)), Ask(BigDecimal(0.5)), Price(BigDecimal(0.7)), Timestamp.now)

  val pairUSDAUD: Rate.Pair = Rate.Pair(Currency.USD, Currency.AUD)
  val rateUSDAUD: Rate =
    Rate(pairUSDAUD, Bid(BigDecimal(0.66)), Ask(BigDecimal(0.88)), Price(BigDecimal(0.55)), Timestamp.now)

  val pairUSDJPY: Rate.Pair = Rate.Pair(Currency.USD, Currency.JPY)
  val rateUSDJPY: Rate =
    Rate(pairUSDJPY, Bid(BigDecimal(0.9)), Ask(BigDecimal(0.5)), Price(BigDecimal(0)), Timestamp.now)

  val allPairs = List(rateUSDEUR, rateUSDAUD, rateUSDJPY)

  // todo - move all assert under for or create common method with for

  test("get rates - successful case for one pair") {
    val fromto = pairUSDEUR.from.toString + pairUSDEUR.to.toString
    val uriStr = s"/rates?pair=${fromto}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](allPairs))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      json <- resp.body.through(utf8Decode).compile.string
      rate <- IO.apply(parseTo[List[GetApiResponse]](json))
    } yield (rate, status)
    val (rateE, status) = rateIO.unsafeRunSync()

    rateE match {
      case Right(rate) => assert(rate == List(rateUSDEUR.asGetRatesResponse.asGetApiResponse))
      case Left(msg) => throw HttpRouteTestHelper.unsatisfiedResponse(msg)
    }
    assert(status == Status.Ok)
  }

  test("get rates - successful case for two pairs") {
    val fromto1 = pairUSDEUR.from.toString + pairUSDEUR.to.toString
    val fromto2 = pairUSDAUD.from.toString + pairUSDAUD.to.toString
    val uriStr = s"/rates?pair=${fromto1}&pair=${fromto2}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](allPairs))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      json <- resp.body.through(utf8Decode).compile.string
      rate <- IO.apply(parseTo[List[GetApiResponse]](json))
    } yield (rate, status)
    val (rateE, status) = rateIO.unsafeRunSync()

    rateE match {
      case Right(rate) => assert(rate.sorted == List(
        rateUSDEUR.asGetRatesResponse.asGetApiResponse,
        rateUSDAUD.asGetRatesResponse.asGetApiResponse
      ).sorted)
      case Left(msg) => throw HttpRouteTestHelper.unsatisfiedResponse(msg)
    }
    assert(status == Status.Ok)
  }

  test("get rates - successful case for empty pair") {
    val uriStr = s"/rates"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](allPairs))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      json <- resp.body.through(utf8Decode).compile.string
      rate <- IO.apply(parseTo[List[GetApiResponse]](json))
    } yield (rate, status)
    val (rateE, status) = rateIO.unsafeRunSync()

    rateE match {
      case Right(rate) => assert(rate == List())
      case Left(msg) => throw HttpRouteTestHelper.unsatisfiedResponse(msg)
    }
    assert(status == Status.Ok)
  }

  test("get rates - not found case for one pair") {
    val fromto = pairUSDEUR.from.toString + pairUSDEUR.to.toString
    val uriStr = s"/rates?pair=${fromto}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](List()))
    val rateIO = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)
    val (notFountMsg, status) = rateIO.unsafeRunSync()

    assert(notFountMsg == Messages.notFoundRateMessage(pairUSDEUR))
    assert(status == Status.NotFound)
  }

  test("get rates - not found case for one of two pairs") {
    val fromto1 = pairUSDEUR.from.toString + pairUSDEUR.to.toString
    val fromto2 = pairUSDAUD.from.toString + pairUSDAUD.to.toString
    val uriStr = s"/rates?pair=${fromto1}&pair=${fromto2}"

    def rateIO(uriStr: String, ratesHttpRoutes: HttpRoutes[IO])  = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)

    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](List(rateUSDEUR)))
    val (notFountMsg, status) = rateIO(uriStr, ratesHttpRoutes).unsafeRunSync()
    assert(notFountMsg == Messages.notFoundRateMessage(pairUSDAUD))
    assert(status == Status.NotFound)
  }

  test("get rates - invalid data case for one pair") {
    val fromto = pairUSDJPY.from.toString + pairUSDJPY.to.toString
    val uriStr = s"/rates?pair=${fromto}"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](allPairs))
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

  test("get rates - invalid data case for one of two pairs") {
    val fromto1 = pairUSDEUR.from.toString + pairUSDEUR.to.toString
    val fromto2 = pairUSDJPY.from.toString + pairUSDJPY.to.toString
    val uriStr = s"/rates?pair=${fromto1}&pair=${fromto2}"

    def rateIO(uriStr: String, ratesHttpRoutes: HttpRoutes[IO])  = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](allPairs))

    val (notFountMsgUSDEUR, statusUSDEUR) = rateIO(uriStr, ratesHttpRoutes).unsafeRunSync()
    assert(notFountMsgUSDEUR == Messages.invalidRateMessage(pairUSDJPY, rateUSDJPY))
    assert(statusUSDEUR == Status.InternalServerError)
  }

  test("get rates - bad request in case of incorrect or unsupported currency in any pair") {
    val incorrectFrom = "QQQ"
    val incorrectTo = "WWW"
    val correctPair = pairUSDEUR.from.toString + pairUSDEUR.to.toString
    val incorrectFromPair = incorrectFrom + pairUSDJPY.to.toString
    val incorrectToPair = pairUSDJPY.from.toString + incorrectTo
    def rateIO(uriStr: String, ratesHttpRoutes: HttpRoutes[IO])  = for {
      resp <- ratesHttpRoutes.orNotFound
        .run(Request(method = Method.GET, uri = Uri.fromString(uriStr).getOrElse(throw HttpRouteTestHelper.incorrectUri)))
      status <- IO(resp.status)
      str <- resp.body.through(utf8Decode).compile.string
    } yield (str, status)
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceList[IO](allPairs))

    val uriStrWithIncorrectPairAsFrom = s"/rates?pair=${correctPair}&pair=${incorrectFromPair}"
    val (errorMsgFrom, statusFrom) = rateIO(uriStrWithIncorrectPairAsFrom, ratesHttpRoutes).unsafeRunSync()
    assert(errorMsgFrom == QueryParams.Messages.invalidCurrency(incorrectFrom))
    assert(statusFrom == Status.BadRequest)

    val uriStrWithIncorrectPairAsTo = s"/rates?pair=${incorrectToPair}&pair=${correctPair}"
    val (errorMsgTo, statusTo) = rateIO(uriStrWithIncorrectPairAsTo, ratesHttpRoutes).unsafeRunSync()
    assert(errorMsgTo == QueryParams.Messages.invalidCurrency(incorrectTo))
    assert(statusTo == Status.BadRequest)

    val uriStrWithIncorrectBoth = s"/rates?pair=${incorrectToPair}&pair=${incorrectFromPair}"
    val (errorMsgBoth, statusBoth) = rateIO(uriStrWithIncorrectBoth, ratesHttpRoutes).unsafeRunSync()
    assert(errorMsgBoth.contains(QueryParams.Messages.invalidCurrency(incorrectTo)))
    assert(errorMsgBoth.contains(QueryParams.Messages.invalidCurrency(incorrectFrom)))
    assert(statusBoth == Status.BadRequest)
  }
}
