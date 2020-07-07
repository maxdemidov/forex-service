package forex.helper

import cats.data.EitherT
import cats.effect.{Clock, Concurrent, ContextShift, Timer}
import cats.implicits._
import forex.domain.Rate
import forex.http.rates.Protocol.GetApiResponse
import forex.http.rates.RatesHttpRoutes
import forex.programs.RatesProgram
import forex.services.RatesCacheService
import forex.services.cache.errors.Error.{RateInvalidFound, RateNotFound}
import forex.services.cache.errors.Messages
import forex.services.cache.{Algebra, errors}
import io.chrisdavenport.log4cats.Logger
import org.http4s.HttpRoutes

object HttpRouteTestHelper {

  val incorrectUri = new Exception("Incorrect URI")
  def unsatisfiedResponse(msg: String) = new Exception(msg)

  implicit def ordering[A <: GetApiResponse]: Ordering[A] = new Ordering[A] {
    override def compare(x: A, y: A): Int = {
      val xs = x.from.toString + x.to.toString
      val ys = y.from.toString + y.to.toString
      xs.compareTo(ys)
    }
  }

  def getRoute[F[_]: Concurrent: Timer: ContextShift: Logger](ratesCacheService: RatesCacheService[F]): HttpRoutes[F] = {
    val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesCacheService)
    val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes
    ratesHttpRoutes
  }

  class RatesCacheServiceEmpty[F[_]: Concurrent: Clock: Logger]() extends Algebra[F] {
    override def get(pairs: List[Rate.Pair]): F[Either[errors.Error, List[Rate]]] = {
      List[Rate]().asRight[errors.Error].pure[F]
    }
  }

  class RatesCacheServiceOne[F[_]: Concurrent: Clock: Logger](rate: Rate) extends Algebra[F] {
    override def get(pairs: List[Rate.Pair]): F[errors.Error Either List[Rate]] = {
      val pair = pairs.head
      val rates = List(rate)
      matchOne[F](pair, rates).map(rateE => rateE.map(rate => List(rate)))
    }
  }

  class RatesCacheServiceList[F[_]: Concurrent: Clock: Logger](rates: List[Rate]) extends Algebra[F] {
    override def get(pairs: List[Rate.Pair]): F[errors.Error Either List[Rate]] = {
      pairs.traverse(pair => EitherT(matchOne[F](pair, rates))).value
    }
  }

  private def matchOne[F[_]: Concurrent: Clock: Logger](pair: Rate.Pair, rates: List[Rate]) = {
    rates.find(rate => pair.from == rate.pair.from && pair.to == rate.pair.to) match {
      case Some(rate) =>
        if ((rate.price.value > 0) && (rate.bid.value > 0) && (rate.ask.value > 0)) {
          rate.asRight[errors.Error].pure[F]
        } else {
          (RateInvalidFound(Messages.invalidRateMessage(pair, rate)): errors.Error).asLeft[Rate].pure[F]
        }
      case None =>
        (RateNotFound(Messages.notFoundRateMessage(pair)): errors.Error).asLeft[Rate].pure[F]
    }
  }
}
