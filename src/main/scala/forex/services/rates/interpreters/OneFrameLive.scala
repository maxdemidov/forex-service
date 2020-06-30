package forex.services.rates.interpreters

import java.sql.{Timestamp => SqlTimestamp}
import java.time.LocalDateTime

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.concurrent.MVar
import cats.implicits._
import forex.domain.Rate
import forex.programs.rates.CacheState
import forex.services.rates.errors._
import forex.services.rates.{Algebra, errors}

class OneFrameLive[F[_]: Applicative : Concurrent] extends Algebra[F] {

  override def get(pair: Rate.Pair,
                   state: F[MVar[F, Option[CacheState]]]): F[Either[errors.Error, Rate]] = {

    // todo:
    //  cache should have timestamp and if it's empty or to old
    //    call to frame and waiting for response
    //    fill cash and answering after that
    //    error instead if cant refresh cache
    //  configurable period for cache
    //  extends configs with token host port for new frame
    //  extends api with ask bid values - show them by special query param
    //  log count of calls to frame per day - process correct error in case they run out off
    //    process parsing errors - set camel case for date from frame in model
    //  test - concurrent first call when cache is empty - they should waiting and only one call should processed to frame
    //  test - if cant get rates from frame in time - call often while didn't get and if cache expired process error correctly
    //  consider to use TRef from ZIO

    val now = LocalDateTime.now()
    for {
      v <- state
      ratesOpt <- v.read
      e <- getRateToEither(pair, ratesOpt, now).pure[F]
    } yield e
  }

  private def getRateToEither(pair: Rate.Pair,
                              chacheStateOpt: Option[CacheState],
                              now: LocalDateTime): Either[Error, Rate] = {
    chacheStateOpt match {
      case Some(cacheState) =>
        if (SqlTimestamp.valueOf(now.minusSeconds(15)).getTime <= SqlTimestamp.valueOf(cacheState.timestamp).getTime) {
          cacheState.rates.get(pair) match {
            case Some(rate) =>
              Right(rate)
            case None =>
              Left(Error.OneFrameLookupFailed("no such pair"))
          }
        } else {
          Left(Error.OneFrameLookupFailed("no actual rate for pair"))
        }
      case None =>
        // todo - wait or left
        Left(Error.OneFrameLookupFailed("no any rates"))
    }
  }
}
