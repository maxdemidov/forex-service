package forex.services.state.interpreters

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent}
import cats.implicits._
import forex.services.state.errors._
import forex.domain.Rate
import forex.services.state.Algebra
import forex.programs.cache.RatesCacheRef
import forex.programs.cache.RatesCacheRef.{ActiveCache, EmptyCache, RatesMap}
import forex.services.state.errors.Error.StateLookupFailed
import io.chrisdavenport.log4cats.Logger

// todo - consider to move it to cache package
class OneStateService[F[_]: Concurrent: Clock: Logger](ratesCacheRef: RatesCacheRef[F]) extends Algebra[F] {

  def get(pair: Rate.Pair): F[Error Either Rate] = {
    for {
      requestTime <- Clock[F].realTime(TimeUnit.MILLISECONDS)
      _ <- Logger[F].debug(s"get rate for pair = [from = ${pair.from}, to = ${pair.to}], requestTime = [$requestTime]")
      ratesCache <- ratesCacheRef.ratesCache.get
      _ <- ratesCache.getsCount.getAndUpdate(_ + 1)
      status <- ratesCache.status.get
      res <- status match {

        case ActiveCache =>
          for {
            _ <- Logger[F].debug("active cache, get rate from map directly")
            ratesMap <- ratesCache.ratesMap.get
            rate <- getRateFromMap(pair, ratesMap)
          } yield rate

        case EmptyCache =>
          for {
            _ <- Logger[F].info("empty cache, rate map should be filled now")
            _ <- ratesCache.toFillTrigger.release
            ratesMap <- ratesCache.ratesMap.get
            rate <- getRateFromMap(pair, ratesMap)
          } yield rate
      }
    } yield res
  }

  private def getRateFromMap(pair: Rate.Pair,
                             ratesMap: RatesMap): F[Either[Error, Rate]] = {
    ratesMap.get(pair) match {
      case Some(rate) =>
        for {
          _ <- Logger[F].debug("getRateToEither rate " + rate)
          rate <- Right(rate).pure[F]
        } yield rate
      case None =>
        for {
          _ <- Logger[F].error(s"getRateToEither no such for rate = [$pair]")
          error <- Left(StateLookupFailed("no such pair")).pure[F]
        } yield error
    }
  }
}
