package forex.cache

import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Timer}
import forex.config.CacheConfig
import forex.helper.CacheProgramHelper
import forex.services.cache.errors
import forex.services.cache.errors.Error.RateNotFound
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class CacheProgramTest extends AnyFunSuite {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val mainEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(mainEC)
  implicit def timer: Timer[IO] = IO.timer(mainEC)

  import CacheProgramHelper._

  test("one call to frame should be perform on start") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 15.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      rates <- Ref[IO].of(CacheProgramHelper.allPairs)
      ratesService: ModifiedCache[IO] = new OneFrameModifiedWithCounter[IO](counter, rates)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val callsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      calls <- cacheEnv.counter.get
      _ = assert(calls == 1)
    } yield ()
    callsIO.unsafeRunSync()
  }

  test("one call to frame in case three successful and one for nonexistent calls to cache") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 15.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      rates <- Ref[IO].of(CacheProgramHelper.allPairs)
      ratesService = new OneFrameModifiedWithCounter[IO](counter, rates)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      rate1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rate2 <- cacheEnv.cacheService.get(List(pairUSDAUD))
      rate3 <- cacheEnv.cacheService.get(List(pairAUDJPY))
      rate4 <- cacheEnv.cacheService.get(List(nonexistentPair))
      _ = assert(rate1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rate2.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDAUD))
      _ = assert(rate3.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateAUDJPY))
      _ = assert(rate4.left.exists(
        error => error == RateNotFound(errors.Messages.notFoundRateMessage(nonexistentPair))))
      _ <- timer.sleep(3.seconds)
      calls <- cacheEnv.counter.get
      _ = assert(calls == 1)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  test("waiting for calls until cache will fill at the start") {
    val responseAfter = 5.seconds
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 20.seconds,
      refreshTimeout = 15.seconds,
      waitTimeout = responseAfter.plus(3.seconds)
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      ratesService = new OneFrameWaitWithCounter[IO](counter, CacheProgramHelper.allPairs, responseAfter)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsBefore <- cacheEnv.counter.get
      _ = assert(callsBefore == 0)
      rate1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rate2 <- cacheEnv.cacheService.get(List(nonexistentPair))
      _ = assert(rate1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rate2.left.exists(
        error => error == RateNotFound(errors.Messages.notFoundRateMessage(nonexistentPair))))
      callsAfter <- cacheEnv.counter.get
      _ = assert(callsAfter == 1)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  test("as any gets received in lazy mode cache always perform only one call to frame and switch to eager mode") {}
  test("call to frame refreshes its under laying state") {}
  test("as no any calls during current refresh period cache changes refreshing mode from eager to lazy without calls to frame") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 12.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set empty state by default")
      rates <- Ref[IO].of(emptyPairs)
      ratesService = new OneFrameModifiedWithCounter[IO](counter, rates)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsAtStart <- cacheEnv.counter.get
      _ <- logger.info("Check that 1 call performed with less then refresh period on start")
      _ = assert(callsAtStart == 1)
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInLazy1AfterRefresh <- cacheEnv.counter.get
      _ <- logger.info("Check that 1 call remains when first refresh period finished")
      _ = assert(callsInLazy1AfterRefresh == 1)
      _ <- timer.sleep(2.second)
      callsInLazy1AfterExpiration <- cacheEnv.counter.get
      _ <- logger.info("Check that 1 call remains when first expiration period finished")
      _ = assert(callsInLazy1AfterExpiration == 1)
      rateBeforeRefresh0 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      _ <- logger.info("Check that on empty state we haven this pair")
      _ = assert(rateBeforeRefresh0.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(pairUSDEUR))))
      _ <- timer.sleep(1.second)
      callsInEager1 <- cacheEnv.counter.get
      _ <- logger.info("Check that 2nd call performed when we were taken some data")
      _ = assert(callsInEager1 == 2)
      _ <- cacheEnv.ratesService.modifyRates(allPairs)
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInEager2BeforeCalls <- cacheEnv.counter.get
      _ <- logger.info("Check that 3nd call performed eagerly because we have one for the previous state")
      _ = assert(callsInEager2BeforeCalls == 3)
      rateAfterRefresh1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rateAfterRefresh2 <- cacheEnv.cacheService.get(List(pairUSDAUD))
      _ <- logger.info("Check that we have new state with rates")
      _ = assert(rateAfterRefresh1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rateAfterRefresh2.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDAUD))
      callsInEager2AfterCalls <- cacheEnv.counter.get
      _ <- logger.info("Check that last gets doesn't triggered some calls to frame")
      _ = assert(callsInEager2AfterCalls == 3)
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInEager3 <- cacheEnv.counter.get
      _ <- logger.info("Check that previous gets switched cache to eager state mode")
      _ = assert(callsInEager3 == 4)
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInLazy2 <- cacheEnv.counter.get
      _ <- logger.info("Check lastly that no any calls to frame performed as no any gets for the previous state")
      _ = assert(callsInLazy2 == 4)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  // todo
}
