package forex.programs.cache

import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import errors._
import forex.config.CacheConfig
import forex.domain.Currency
import forex.programs.cache.CashType.RatesMap
import forex.services.RatesService
import forex.programs.cache.errors.Error.{CacheRefreshOneFrameError, CacheRefreshParseResponseFailed, CacheRefreshRequestFailed, CacheRefreshTimeoutExceeded}
import forex.services.state.Algebra
import forex.services.state.interpreters.OneStateService
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

object AutoRefreshedCache {

  val emptyCache: Option[CacheState] = None

  val errorOnTimeout: Error = CacheRefreshTimeoutExceeded("Cash refresh timeout exceeded")

  def initiate[F[_]: Concurrent: Timer: ContextShift : Logger](config: CacheConfig,
                                                               ratesService: RatesService[F],
                                                               blockingEC: ExecutionContextExecutor): F[Algebra[F]] = {

    def performRefresh(state: Ref[F, Option[CacheState]]): F[Unit] = {
      for {
        _ <- Logger[F].debug("perform refresh")
        timestamp <- Timer[F].clock.realTime(TimeUnit.MICROSECONDS)
        frameResponse <- ContextShift[F].shift *> performTimedRefresh()
        _ <- frameResponse match {
          case Right(ratesMap) =>
            for {
              _ <- Logger[F].debug("correct frame response")
              _ <- state.set(Some(CacheState(timestamp, ratesMap)))
              _ <- scheduleNext(config.refreshTimeout, state)
            } yield ()
          case Left(error: Error) =>
            for {
              _ <- Logger[F].debug("error frame response")
              _ <- error match {
                case CacheRefreshTimeoutExceeded(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshTimeoutExceeded msg = " + msg)
                    _ <- scheduleNext(FiniteDuration(5, TimeUnit.SECONDS), state)
                  } yield ()
                case CacheRefreshParseResponseFailed(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshParseResponseFailed msg = " + msg)
                    _ <- scheduleNext(FiniteDuration(2, TimeUnit.SECONDS), state)
                  } yield ()
                case CacheRefreshRequestFailed(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshRequestFailed msg = " + msg)
                    _ <- scheduleNext(FiniteDuration(2, TimeUnit.SECONDS), state)
                  } yield ()
                case CacheRefreshOneFrameError(msg) =>
                  for {
                    _ <- Logger[F].error("CacheRefreshOneFrameError msg = " + msg)
                    _ <- scheduleNext(FiniteDuration(30, TimeUnit.SECONDS), state)
                  } yield ()
              }
            } yield ()
        }
      } yield ()
    }

    def performTimedRefresh(): F[Either[errors.Error, RatesMap]] = {
      val apiCall = ratesService.refresh(Currency.allPairs).map(_.leftMap(toCacheError))
      val timeoutToGet: FiniteDuration = config.waitTimeout
      Concurrent.timeoutTo(
        ContextShift[F].evalOn(blockingEC)(apiCall), timeoutToGet, errorOnTimeout.asLeft[RatesMap].pure[F]
      )
    }

    def scheduleNext(scheduleDuration: FiniteDuration,
                     state: Ref[F, Option[CacheState]]): F[Unit] = {
      for {
        c <- Timer[F].sleep(scheduleDuration)
        _ <- Concurrent[F].start(performRefresh(state))
      } yield c
    }

    for {
      state <- Ref[F].of(emptyCache)
      _ <- Concurrent[F].start(performRefresh(state))
    } yield new OneStateService[F](state)
  }

//  override def refresh(): F[Error Either Option[CacheState]] = {
//    stateService.refresh().flatMap {
//      case Right(s) =>
//        val ns = for {
//          _ <- ref.set(s)
//          n <- ref.get
//        } yield n
//        ns.map(v => v.asRight[Error])
//      case Left(g) =>
//        toProgramError(g).asLeft[Option[CacheState]].pure[F]
//    }
//  }
}
