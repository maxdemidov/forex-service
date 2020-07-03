package forex.services.state.interpreters

import java.sql.{Timestamp => SQLTimestamp}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Concurrent}
import cats.effect.concurrent.Ref
import cats.implicits._
import forex.services.state.errors._
import forex.domain.Rate
import forex.services.state.Algebra
import forex.programs.cache.CacheState
import io.chrisdavenport.log4cats.Logger

class OneStateService[F[_]: Concurrent: Clock: Logger](state: Ref[F, Option[CacheState]]) extends Algebra[F] {

  private def minus(ts: Long) = {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC)
    SQLTimestamp.valueOf(ldt.minusSeconds(15)).getTime
  }

  def get(pair: Rate.Pair): F[Error Either Rate] = {
    for {
      timestamp <- Clock[F].realTime(TimeUnit.MICROSECONDS)
      cache <- state.get
      v <- getRateToEither(pair, cache, timestamp)
    } yield v

    //    val now = LocalDateTime.now()
    //    for {
    //      cache <- state.flatMap(_.get)
    //      v <- getRateToEither(pair, cache, now).pure[F]
    //    } yield v

    //    for {
    //      v <- state
    //      ratesOpt <- v.read
    //      e <- getRateToEither(pair, ratesOpt, now).pure[F]
    //    } yield e
    //    getRateToEither(pair, state, now).pure[F]
  }

  private def getRateToEither(pair: Rate.Pair,
                              cacheStateOpt: Option[CacheState],
                              timestamp: Long): F[Either[Error, Rate]] = {
    cacheStateOpt match {
      case Some(cacheState) =>
        if (minus(timestamp) <= cacheState.timestamp) {
          cacheState.rates.get(pair) match {
            case Some(rate) =>
              for {
                _ <- Logger[F].debug("getRateToEither rate " + rate)
                rate <- Right(rate).pure[F]
              } yield rate
            case None =>
              for {
                _ <- Logger[F].error(s"getRateToEither no such for rate = [$pair]")
                error <- Left(Error.StateLookupFailed("no such pair")).pure[F]
              } yield error
          }
        } else {
          for {
            _ <- Logger[F].error(s"getRateToEither incorrect time now = [$timestamp] <= [${cacheState.timestamp}]")
            error <- Left(Error.StateLookupFailed("no actual rate for pair")).pure[F]
          } yield error
        }
      case None =>
        // todo - wait or left
        for {
          _ <- Logger[F].error("getRateToEither None")
          error <- Left(Error.StateLookupFailed("no any rates")).pure[F]
        } yield error
    }
  }
}
