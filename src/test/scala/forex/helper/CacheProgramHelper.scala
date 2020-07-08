package forex.helper

import java.util.concurrent.Executors

import cats.Applicative
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import forex.config.{CacheConfig, HistoryConfig}
import forex.domain.types.RateTypes.RatesList
import forex.domain.{Ask, Bid, Currency, Price, Rate, Timestamp}
import forex.programs.CacheProgram
import forex.programs.cache.CacheState
import forex.services.rates.{Algebra => RatesAlgebra}
import forex.services.cache.{Algebra => CacheAlgebra}
import forex.services.rates.errors.Error
import forex.services.{CacheRatesServices, CallsHistoryService, CallsHistoryServices, RatesService}
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object CacheProgramHelper {

  trait ModifiedCache[F[_]] extends RatesAlgebra[F] {
    def modifyRates(list: List[Rate]): F[Unit]
    def modifySleep(duration: FiniteDuration): F[Unit]
  }

  case class CacheEnv[F[_]: Sync, A <: ModifiedCache[F]](cacheService: CacheAlgebra[F],
                                                         ratesService: A,
                                                         counter: Ref[F, Int])

  private val blockingEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  val pairUSDEUR: Rate.Pair = Rate.Pair(Currency.USD, Currency.EUR)
  val rateUSDEUR: Rate =
    Rate(pairUSDEUR, Bid(BigDecimal(0.9)), Ask(BigDecimal(0.5)), Price(BigDecimal(0.7)), Timestamp.now)
  val pairUSDAUD: Rate.Pair = Rate.Pair(Currency.USD, Currency.AUD)
  val rateUSDAUD: Rate =
    Rate(pairUSDAUD, Bid(BigDecimal(0.66)), Ask(BigDecimal(0.88)), Price(BigDecimal(0.55)), Timestamp.now)
  val pairAUDJPY: Rate.Pair = Rate.Pair(Currency.AUD, Currency.JPY)
  val rateAUDJPY: Rate =
    Rate(pairAUDJPY, Bid(BigDecimal(0.777)), Ask(BigDecimal(0.731)), Price(BigDecimal(0.753)), Timestamp.now)

  val allPairs = List(rateUSDEUR, rateUSDAUD, rateAUDJPY)
  val emptyPairs: List[Rate] = List[Rate]()

  val nonexistentPair: Rate.Pair = Rate.Pair(Currency.GBP, Currency.NZD)

  val tryToGetLeft = new Exception("Either try to get left")

  def getCacheService[F[_]: Concurrent: Timer: ContextShift: Logger](ratesService: RatesService[F],
                                                                     configCache: CacheConfig): F[CacheAlgebra[F]] = {

    val historyService: CallsHistoryService[F] = CallsHistoryServices.queue[F](HistoryConfig(historyLiveTimeout = 1.minutes))

    for {
      cacheState <- CacheState.initial[F]
      cacheProgram <- CacheProgram[F](configCache, ratesService, historyService, cacheState, blockingEC).pure[F]
      cacheService <- CacheRatesServices.cached(cacheProgram).pure[F]
      _ <- Concurrent[F].start(cacheProgram.startAutoRefreshableCache())
    } yield cacheService
  }

  class OneFrameModified[F[_]: Applicative: Concurrent: Timer: Logger](counter: Ref[F, Int],
                                                                       rates: Ref[F, List[Rate]],
                                                                       sleep: Ref[F, FiniteDuration]) extends ModifiedCache[F] {

    override def get(pairs: List[Rate.Pair]): F[Error Either RatesList] = {
      for {
        _ <- Logger[F].info("OneFrameModified - frame touched")
        duration <- sleep.get
        _ <- Timer[F].sleep(duration)
        count <- counter.getAndUpdate(_ + 1)
        rates <- rates.get
        res <- rates.asRight[Error].pure[F]
        _ <- Logger[F].info(s"OneFrameModified - frame counter increased up to [$count]")
      } yield res
    }

    override def modifyRates(list: List[Rate]): F[Unit] =
      for { _ <- rates.getAndUpdate(_ => list)} yield ()

    override def modifySleep(duration: FiniteDuration): F[Unit] =
      for {_ <- sleep.getAndUpdate(_ => duration) } yield ()
  }
}
