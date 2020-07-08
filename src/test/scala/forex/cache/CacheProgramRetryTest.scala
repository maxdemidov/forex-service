package forex.cache

import java.util.concurrent.{Executors, TimeUnit}

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, IO, Timer}
import forex.config.CacheConfig
import forex.helper.CacheProgramHelper
import forex.services.cache.errors
import forex.services.cache.errors.Error.RateNotFound
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

// todo - in progress
class CacheProgramRetryTest extends AnyFunSuite {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val mainEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(mainEC)
  implicit def timer: Timer[IO] = IO.timer(mainEC)

  import CacheProgramHelper._

  // todo - need to minimize timeouts for test

  ignore("cache in eager mode should take frame for rates after refresh period with retry") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 12.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 5.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set empty state by default")
      rates <- Ref[IO].of(allPairs)
      duration <- Ref[IO].of(1.second)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      _ <- timer.sleep(3.seconds)
      callsAtStart <- cacheEnv.counter.get
      _ <- logger.info("Check for firs call to frame")
      _ = assert(callsAtStart == 1)
      rateBeforeRefresh <- cacheEnv.cacheService.get(List(pairUSDEUR))
      _ <- logger.info("Check that cache have rates")
      _ = assert(rateBeforeRefresh.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ <- logger.info("Modify state on dummy frame with new data and longer the waiting timeout duration to obtain rates")
      _ <- cacheEnv.ratesService.modifySleep(8.seconds)
      _ <- cacheEnv.ratesService.modifyRates(emptyPairs)
      _ <- logger.info("Wait until refresh period finished")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      _ <- logger.info("Wait for retry")
      _ <- timer.sleep(5.second)
      callsToFrame <- cacheEnv.counter.get
      _ <- logger.info("Check that next call to frame not occur yet")
      _ = assert(callsToFrame == 1)

      _ <- logger.info("Perform one get")
      rateAfterRetryFinishedF <- Concurrent[IO].start(cacheEnv.cacheService.get(List(pairUSDEUR)))
      _ <- logger.info("Modify state on dummy frame with shorter the waiting timeout duration to obtain rates")
      _ <- cacheEnv.ratesService.modifySleep(2.seconds)
      _ <- cacheEnv.ratesService.modifyRates(emptyPairs)
      rateAfterRetryFinished <- rateAfterRetryFinishedF.join

      _ <- logger.info("Check that cache returns new rates")
      _ = assert(rateAfterRetryFinished.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(pairUSDEUR))))
      callsToFrame <- cacheEnv.counter.get
      _ <- logger.info("Check that there three calls to frame")
      _ = assert(callsToFrame == 3)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  ignore("cache in lazy mode should return its rates after refresh period until expired period finished") {
    val configCache: CacheConfig = CacheConfig(
      expirationTimeout = 15.seconds,
      refreshTimeout = 10.seconds,
      waitTimeout = 10.seconds
    )
    val cacheEnvIO = for {
      counter <- Ref[IO].of(0)
      _ <- logger.info("Set empty state by default")
      rates <- Ref[IO].of(allPairs)
      duration <- Ref[IO].of(1.second)
      ratesService = new OneFrameModified[IO](counter, rates, duration)
      cacheService <- CacheProgramHelper.getCacheService[IO](ratesService, configCache)
    } yield CacheEnv(cacheService, ratesService, counter)

    val resultsIO = for {
      cacheEnv <- cacheEnvIO
      rateBeforeRefresh <- cacheEnv.cacheService.get(List(pairUSDEUR))
      _ <- logger.info("Check that cache have rates")
      _ = assert(rateBeforeRefresh.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ <- logger.info("Modify state with long duration to obtain rates for dummy frame")
      _ <- cacheEnv.ratesService.modifySleep(8.seconds)
      _ <- cacheEnv.ratesService.modifyRates(emptyPairs)
      _ <- logger.info("Wait until refresh period finished")
      _ <- timer.sleep(configCache.refreshTimeout.plus(1.second))
      rateAfterRefresh <- cacheEnv.cacheService.get(List(pairUSDEUR))
      _ <- logger.info("Check that cache returns rates")
      _ = assert(rateAfterRefresh.getOrElse(throw CacheProgramHelper.tryToGetLeft) == List(rateUSDEUR))
      _ <- logger.info("Sleep until expiration period finished")
      _ <- timer.sleep(5.seconds)
      callsBefore <- cacheEnv.counter.get
      _ <- logger.info("Check that second call to frame not completed")
      _ = assert(callsBefore == 1)

      timeBefore <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      rateAfterCacheLive <- cacheEnv.cacheService.get(List(pairUSDEUR))
      timeAfter <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      _ <- logger.info("Check that timeout grater then sleep on frame")

      _ <- logger.info(s"timeAfter = ${timeAfter} - timeBefore = ${timeBefore} = diff = ${timeAfter - timeBefore}, 4.seconds.toMillis = ${4.seconds.toMillis}")

      _ <- logger.info("Check that cache returns new data")
      _ = assert(rateAfterCacheLive.left.exists(error => error == RateNotFound(errors.Messages.notFoundRateMessage(pairUSDEUR))))

      callsAfter <- cacheEnv.counter.get
      _ <- logger.info("Check that second call completed")
      _ = assert(callsAfter == 2)
    } yield ()
    resultsIO.unsafeRunSync()
  }

  ignore("check retry in case dummy frame returns errors") {
    // todo - test for retry
  }

  ignore("check retry in case dummy frame timed longer then wait timeout") {
    // todo - test for retry
  }
}
