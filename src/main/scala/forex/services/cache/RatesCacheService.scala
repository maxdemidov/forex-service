package forex.services.cache

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent}
import cats.implicits._
import forex.domain.Rate
import forex.programs.cache.RatesCacheRef
import forex.programs.cache.RatesCacheRef.RatesMap
import forex.services.cache.errors.Error.StateLookupFailed
import forex.services.cache.errors._
import io.chrisdavenport.log4cats.Logger

// todo - consider to move it to cache package
class RatesCacheService[F[_]: Concurrent: Clock: Logger](ratesCacheRef: RatesCacheRef[F]) extends Algebra[F] {

  def get(pair: Rate.Pair): F[Error Either Rate] = {
    for {
      requestTime <- Clock[F].realTime(TimeUnit.MILLISECONDS)
      _ <- Logger[F].debug(s"get rate for pair = [from = ${pair.from}, to = ${pair.to}], requestTime = [$requestTime]")
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
          _ <- Logger[F].debug(s"find rate = [$rate] for pair = [$pair]")
          rate <- Right(rate).pure[F]
        } yield rate
      case None =>
        for {
          _ <- Logger[F].error(s"no such for rate for pair = [$pair]")
          error <- Left(StateLookupFailed("no such pair")).pure[F]
        } yield error
    }
  }
}
