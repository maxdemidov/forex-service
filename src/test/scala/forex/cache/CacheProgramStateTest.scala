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

class CacheProgramStateTest extends AnyFunSuite {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val mainEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(mainEC)
  implicit def timer: Timer[IO] = IO.timer(mainEC)

  import CacheProgramHelper._

  // todo - need to minimize timeouts for test

  test("one call to frame should be perform on start") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 15.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set all pairs state by default")
      rates <- Ref[IO].of(CacheProgramHelper.allPairs)
      duration <- Ref[IO].of(100.milliseconds)
      ratesService: ModifiedCache[IO] = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val callsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      calls <- cacheEnv.counter.get
      _ <- logger.info("Check that frame was touched only once at the start")
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
      _ <- logger.info("Set all pairs state by default")
      rates <- Ref[IO].of(CacheProgramHelper.allPairs)
      duration <- Ref[IO].of(100.milliseconds)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      _ <- logger.info("Perform four calls to the cache")
      rate1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rate2 <- cacheEnv.cacheService.get(List(pairUSDAUD))
      rate3 <- cacheEnv.cacheService.get(List(pairAUDJPY))
      rate4 <- cacheEnv.cacheService.get(List(nonexistentPair))
      _ <- logger.info("Check that they processed correctly")
      _ = assert(rate1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rate2.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDAUD))
      _ = assert(rate3.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateAUDJPY))
      _ = assert(rate4.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(nonexistentPair))))
      _ <- timer.sleep(3.seconds)
      _ <- logger.info("Check that frame was touched only once")
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
      _ <- logger.info("Set all pairs state by default")
      rates <- Ref[IO].of(CacheProgramHelper.allPairs)
      duration <- Ref[IO].of(responseAfter)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsBefore <- cacheEnv.counter.get
      _ <- logger.info("Check that no any calls performed on start due to long response time from dummy")
      _ = assert(callsBefore == 0)
      _ <- logger.info("Touch cache with two calls, that should wait for filling cache")
      rate1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rate2 <- cacheEnv.cacheService.get(List(nonexistentPair))
      _ <- logger.info("Check that clients receive appropriate rates")
      _ = assert(rate1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rate2.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(nonexistentPair))))
      callsAfter <- cacheEnv.counter.get
      _ <- logger.info("Check that frame was touched only once")
      _ = assert(callsAfter == 1)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  test("as no any calls during current refresh period cache switch refresh mode from eager to lazy without calls to frame") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 12.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set empty state by default")
      rates <- Ref[IO].of(emptyPairs)
      duration <- Ref[IO].of(100.milliseconds)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsAtStart <- cacheEnv.counter.get
      _ <- logger.info("Check that first call performed with less then refresh period finished on start")
      _ = assert(callsAtStart == 1)
      _ <- logger.info("Touch cache with one call to make it eager")
      _ <- cacheEnv.cacheService.get(List(pairUSDEUR))
      _ <- logger.info("Sleep until refresh period finished")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInEager <- cacheEnv.counter.get
      _ <- logger.info("Check that second call to frame ware performed")
      _ = assert(callsInEager == 2)
      _ <- logger.info("Sleep until refresh period finished for untouchable cache")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInLazy <- cacheEnv.counter.get
      _ <- logger.info("Check that no any calls to frame performed as cache switched at lazy mode")
      _ = assert(callsInLazy == 2)
      _ <- logger.info("Sleep until expiration period finished")
      _ <- timer.sleep(2.second)
      callsInLazyAfterExpiration <- cacheEnv.counter.get
      _ <- logger.info("Check that the same count of calls remains when expiration period finished too")
      _ = assert(callsInLazyAfterExpiration == 2)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  test("as any gets have received in lazy mode cache process them with new rates and with only one call to frame") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 12.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set empty state by default")
      rates <- Ref[IO].of(emptyPairs)
      duration <- Ref[IO].of(100.milliseconds)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsAtStart <- cacheEnv.counter.get
      _ <- logger.info("Check that first call performed with less then refresh period finished on start")
      _ = assert(callsAtStart == 1)
      _ <- logger.info("Sleep until refresh period finished for untouchable cache")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInLazy <- cacheEnv.counter.get
      _ <- logger.info("Check that no any calls to frame performed as cache switched at lazy mode")
      _ = assert(callsInLazy == 1)
      _ <- logger.info("Sleep until expiration period finished")
      _ <- timer.sleep(2.second)
      callsInLazyAfterExpiration <- cacheEnv.counter.get
      _ <- logger.info("Check that the same count af calls remains when expiration period finished too")
      _ = assert(callsInLazyAfterExpiration == 1)
      _ <- logger.info("Modify state with new rates for dummy frame")
      _ <- cacheEnv.ratesService.modifyRates(allPairs)
      _ <- logger.info("Touch cache with gets should refresh cache at once and process gets with new rates")
      rateAfterRefresh1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rateAfterRefresh2 <- cacheEnv.cacheService.get(List(pairUSDAUD))
      _ <- logger.info("Check that we have new state with rates")
      _ = assert(rateAfterRefresh1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rateAfterRefresh2.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDAUD))
      callsInAfterLazy <- cacheEnv.counter.get
      _ <- logger.info("Check that only one second call to frame ware performed")
      _ = assert(callsInAfterLazy == 2)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  test("any call in lazy mode switch cache to eager mode") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 12.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set empty state by default")
      rates <- Ref[IO].of(emptyPairs)
      duration <- Ref[IO].of(100.milliseconds)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsAtStart <- cacheEnv.counter.get
      _ <- logger.info("Check that first call performed with less then refresh period finished on start")
      _ = assert(callsAtStart == 1)
      _ <- logger.info("Sleep until refresh period finished")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInLazy <- cacheEnv.counter.get
      _ <- logger.info("Check that one call remains when first refresh period finished")
      _ = assert(callsInLazy == 1)
      _ <- logger.info("Sleep until expiration period finished")
      _ <- timer.sleep(2.second)
      callsInLazyAfterExpiration <- cacheEnv.counter.get
      _ <- logger.info("Check that the same count af calls remains when expiration period finished too")
      _ = assert(callsInLazyAfterExpiration == 1)
      _ <- logger.info("Touch cache with one call to make it eager")
      _ <- cacheEnv.cacheService.get(List(pairUSDEUR))
      callsInEagerAfterCall <- cacheEnv.counter.get
      _ <- logger.info("Check that last get triggered second call to frame")
      _ = assert(callsInEagerAfterCall == 2)
      _ <- logger.info("Sleep until refresh period finished")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      callsInEagerNextCall <- cacheEnv.counter.get
      _ <- logger.info("Check that previous gets switched cache to eager state mode and third call to frame performed")
      _ = assert(callsInEagerNextCall == 3)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  test("cache refresh its underlying state with new rates after call to frame") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 12.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set all pairs state by default")
      rates <- Ref[IO].of(allPairs)
      duration <- Ref[IO].of(100.milliseconds)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      _ <- logger.info("Perform two gets")
      rateBeforeRefresh1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rateBeforeRefresh2 <- cacheEnv.cacheService.get(List(pairUSDAUD))
      _ <- logger.info("Check that cache have rates")
      _ = assert(rateBeforeRefresh1.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ = assert(rateBeforeRefresh2.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDAUD))
      _ <- logger.info("Modify state with empty rates for dummy frame")
      _ <- cacheEnv.ratesService.modifyRates(emptyPairs)
      _ <- logger.info("Wait until refresh period finished")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      _ <- logger.info("Perform two gets")
      rateAfterRefresh1 <- cacheEnv.cacheService.get(List(pairUSDEUR))
      rateAfterRefresh2 <- cacheEnv.cacheService.get(List(pairUSDAUD))
      _ <- logger.info("Check that cache have new state after refresh")
      _ = assert(rateAfterRefresh1.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(pairUSDEUR))))
      _ = assert(rateAfterRefresh2.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(pairUSDAUD))))
    } yield ()
    resultsIO.unsafeRunSync()
  }
}
