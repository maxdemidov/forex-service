package forex.services.cache.interpreters

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent}
import cats.implicits._
import forex.common.datetime.DateTimeConverters
import forex.domain.Rate
import forex.domain.types.RateTypes.RatesMap
import forex.programs.CacheProgram
import forex.services.cache.Algebra
import forex.services.cache.errors.Error.RatesLookupFailed
import forex.services.cache.errors._
import io.chrisdavenport.log4cats.Logger

class RatesCacheService[F[_]: Concurrent: Clock: Logger](cacheProgram: CacheProgram[F]) extends Algebra[F] {

  // todo - make one instead of two - only for list
  def get(pair: Rate.Pair): F[Error Either Rate] = {
    for {
      requestDateTime <- Clock[F].realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
      _ <- Logger[F].debug(s"Get rate for pair = [$pair], requestTime = [$requestDateTime]")
      ratesMap <- cacheProgram.obtainCachedMap
      rate <- getRateFromMap(pair, ratesMap)
    } yield rate
  }

  def get(pairs: List[Rate.Pair]): F[List[Error Either Rate]] = {
    for {
      requestDateTime <- Clock[F].realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
      _ <- Logger[F].debug(s"Get rate for pairs = [$pairs], requestTime = [$requestDateTime]")
      ratesMap <- cacheProgram.obtainCachedMap
      rate <- getRatesFromMap(pairs, ratesMap)
    } yield rate
  }

  private def getRatesFromMap(pairs: List[Rate.Pair],
                              ratesMap: RatesMap): F[List[Error Either Rate]] = {
    pairs.traverse(pair => getRateFromMap(pair, ratesMap))
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
