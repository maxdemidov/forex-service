package forex.services.cache.interpreters

import java.util.concurrent.TimeUnit

import cats.Applicative
import cats.data.EitherT
import cats.effect.{Clock, Concurrent}
import cats.implicits._
import forex.common.datetime.DateTimeConverters
import forex.domain.{Price, Rate}
import forex.domain.types.RateTypes.RatesMap
import forex.programs.CacheProgram
import forex.services.cache.Algebra
import forex.services.cache.errors.Error.{RateInvalidFound, RateNotFound}
import forex.services.cache.errors._
import io.chrisdavenport.log4cats.Logger

class RatesCacheService[F[_]: Applicative: Concurrent: Clock: Logger](cacheProgram: CacheProgram[F]) extends Algebra[F] {

  def get(pair: Rate.Pair): F[Error Either Rate] = {
    for {
      requestDateTime <- Clock[F].realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
      _ <- Logger[F].debug(s"Get rate for pair = [$pair], requestTime = [$requestDateTime]")
      ratesMap <- cacheProgram.obtainCachedMap
      rate <- getRateFromMap(pair, ratesMap)
    } yield rate
  }

  def get(pairs: List[Rate.Pair]): F[Error Either List[Rate]] = {
    for {
      requestDateTime <- Clock[F].realTime(TimeUnit.MILLISECONDS).map(DateTimeConverters.toDateTimeFormat)
      _ <- Logger[F].debug(s"Get rate for pairs = [$pairs], requestTime = [$requestDateTime]")
      ratesMap <- cacheProgram.obtainCachedMap
      rate <- getRatesFromMap(pairs, ratesMap)
    } yield rate
  }

  private def getRatesFromMap(pairs: List[Rate.Pair],
                              ratesMap: RatesMap): F[Error Either List[Rate]] = {
    pairs.traverse(pair => EitherT(getRateFromMap(pair, ratesMap))).value
  }

  private def getRateFromMap(pair: Rate.Pair,
                             ratesMap: RatesMap): F[Either[Error, Rate]] = {
    ratesMap.get(pair) match {
      case Some(rate) if isValid(rate) =>
        for {
          _ <- Logger[F].debug(Messages.foundRateMessage(pair, rate))
          rate <- Right(rate).pure[F]
        } yield rate
      case Some(rate) =>
        for {
          _ <- Logger[F].warn(Messages.invalidRateMessage(pair, rate))
          error <- Left(RateInvalidFound(Messages.invalidRateMessage(pair, rate))).pure[F]
        } yield error
      case None =>
        for {
          _ <- Logger[F].info(Messages.notFoundRateMessage(pair))
          error <- Left(RateNotFound(Messages.notFoundRateMessage(pair))).pure[F]
        } yield error
    }
  }

  private def isValid(rate: Rate) = !Price.isEmpty(rate.price)

  object Messages {
    def invalidRateMessage(pair: Rate.Pair, rate: Rate) = s"Found invalid rate for pair = [$pair], rate = [$rate]"
    def foundRateMessage(pair: Rate.Pair, rate: Rate) = s"Found rate for pair = [$pair], rate = [$rate]"
    def notFoundRateMessage(pair: Rate.Pair) = s"No found rate for pair = [$pair]"
  }
}
