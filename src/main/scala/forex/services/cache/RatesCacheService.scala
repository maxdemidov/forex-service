package forex.services.cache

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent}
import cats.implicits._
import forex.common.datetime.DateTimeConverters
import forex.domain.Rate
import forex.domain.RateTypes.RatesMap
import forex.programs.cache.RatesCacheRef
import forex.services.cache.errors.Error.RatesLookupFailed
import forex.services.cache.errors._
import io.chrisdavenport.log4cats.Logger

// todo - consider to move it to cache package
class RatesCacheService[F[_]: Concurrent: Clock: Logger](ratesCacheRef: RatesCacheRef[F]) extends Algebra[F] {

  def get(pair: Rate.Pair): F[Error Either Rate] = {
    for {
      requestDateTime <- Clock[F].realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
      _ <- Logger[F].debug(s"Get rate for pair = [$pair], requestTime = [$requestDateTime]")
      ratesCache <- ratesCacheRef.ratesCache.get
      _ <- ratesCache.calls.release
      ratesMap <- ratesCache.ratesMap.get
      rate <- getRateFromMap(pair, ratesMap)
    } yield rate
  }

  private def getRateFromMap(pair: Rate.Pair,
                             ratesMap: RatesMap): F[Either[Error, Rate]] = {
    ratesMap.get(pair) match {
      case Some(rate) =>
        for {
          _ <- Logger[F].debug(s"Find rate for pair = [$pair], rate = [$rate]")
          rate <- Right(rate).pure[F]
        } yield rate
      case None =>
        for {
          _ <- Logger[F].info(s"No such rate for pair = [$pair]")
          error <- Left(RatesLookupFailed("No such pair")).pure[F]
        } yield error
    }
  }
}
