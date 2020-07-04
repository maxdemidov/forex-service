package forex.programs.cache

import cats.Monad
import forex.config.CacheConfig
import cats.implicits._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object CacheUtil {

  def nextRetryTimeoutOrElse[F[_]: Monad](config: CacheConfig,
                                          defaultTimeout: FiniteDuration)
                                         (implicit stateRef: RatesStateRef[F]): F[FiniteDuration] = {

    def getNextTimeout(currentTimeout: FiniteDuration): FiniteDuration = {
      val factor = if (currentTimeout.lt(1.second)) 5 else if (currentTimeout.lt(10.second)) 2 else 1.5
      val nextTimeout = (currentTimeout.toMillis * factor).ceil.toInt.milliseconds
      if (nextTimeout.gt(config.refreshTimeout)) config.refreshTimeout else nextTimeout
    }

    for {
      timeout <- stateRef.nextRetryAfter.tryTake.map(_.getOrElse(defaultTimeout))
      _ <- stateRef.nextRetryAfter.tryPut(getNextTimeout(timeout))
    } yield timeout
  }
}
