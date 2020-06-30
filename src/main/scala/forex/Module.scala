package forex

import cats.effect.{Concurrent, Timer}
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import forex.programs.rates.CacheState
import fs2.Stream
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}
import forex.programs.rates.errors._

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig) {

  private val ratesService: RatesService[F] = RatesServices.live[F]()
  private val cacheService: CacheService[F] = CacheServices.live[F]()

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService, cacheService)

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

  val ratesRefresh: Stream[F, Either[Error, Option[CacheState]]] = {
    Stream.eval(ratesProgram.refresh()) ++
      (
        Stream.sleep(config.frame.refreshTimeout).drain ++
        Stream.eval(ratesProgram.refresh())
      ).repeat
  }
}
