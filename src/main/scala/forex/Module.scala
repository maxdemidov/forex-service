package forex

import cats.effect.{Concurrent, ContextShift, Timer}
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import forex.programs.cache.CacheState
import io.chrisdavenport.log4cats.Logger
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}

import scala.concurrent.ExecutionContextExecutor

class Module[F[_]: Concurrent: ContextShift: Timer: Logger](config: ApplicationConfig,
                                                            cacheState: CacheState[F],
                                                            blockingEC: ExecutionContextExecutor) {

  private val ratesService: RatesService[F] = RatesServices.live[F](config.frame)
  private val historyService: CallsHistoryService[F] = CallsHistoryServices.queue[F](config.history)

  private val cacheProgram: CacheProgram[F] =
    CacheProgram[F](config.cache, ratesService, historyService, cacheState, blockingEC)

  private val ratesCacheService: RatesCacheService[F] = CacheRatesServices.live(cacheProgram)

  private val ratesProgram: RatesProgram[F] =
    RatesProgram[F](ratesCacheService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.requestTimeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

  val startAutoRefreshableCache: F[Unit] = cacheProgram.startAutoRefreshableCache()
}
