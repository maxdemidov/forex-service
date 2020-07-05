package forex

import java.util.concurrent.Executors

import cats.effect._
import cats.syntax.applicative._
import forex.config._
import forex.programs.cache.AutoRefreshedCache
import forex.services.RatesServices
import forex.services.CallsHistoryServices
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Main extends IOApp {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  val blockingEC: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO](blockingEC).stream.compile.drain.as(ExitCode.Success)
}

class Application[F[_]: ConcurrentEffect: Timer: ContextShift: Logger](blockingEC: ExecutionContextExecutor) {

  def stream: Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      ratesService <- Stream.eval(RatesServices.live[F](config.frame).pure[F])
      callsHistoryService <- Stream.eval(CallsHistoryServices.queue[F](config.history).pure[F])
      // todo - create cache service outside of cache object
      cacheService <- Stream.eval(
        AutoRefreshedCache.initiate(config.cache, ratesService, callsHistoryService, blockingEC)
      )
      module = new Module[F](config.http, cacheService)
      _ <- BlazeServerBuilder[F]
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()
}
