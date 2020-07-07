package forex.http

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import forex.common.parser.CommonJsonParser
import forex.domain._
import forex.helper.HttpRouteTestHelper
import forex.helper.HttpRouteTestHelper.RatesCacheServiceEmpty
import fs2.text.utf8Decode
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.implicits._
import org.http4s.{Method, Request, _}
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class HttpApiCurrenciesEndpointTest extends AnyFunSuite with CommonJsonParser {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val mainEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(mainEC)
  implicit def timer: Timer[IO] = IO.timer(mainEC)

  test("currencies - list of available") {
    val uriCurrencies = uri"/rates/currencies"
    val ratesHttpRoutes = HttpRouteTestHelper.getRoute[IO](new RatesCacheServiceEmpty[IO])
    val currenciesIO = for {
      resp <- ratesHttpRoutes.orNotFound.run(Request(method = Method.OPTIONS, uri = uriCurrencies))
      status <- IO(resp.status)
      json <- resp.body.through(utf8Decode).compile.string
      currencies <- IO.apply(parseTo[List[Currency]](json) match {
        case Right(list) => list
        case Left(_)     => List()
      })
    } yield (currencies, status)
    val (currencies, status) = currenciesIO.unsafeRunSync()

    assert(currencies.map(Currency.show.show).sorted == Currency.allCurrencies.map(Currency.show.show).sorted)
    assert(status == Status.Ok)
  }
}
