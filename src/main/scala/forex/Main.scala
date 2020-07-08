package forex

import java.util.concurrent.Executors

import cats.effect._
import forex.config._
import forex.programs.cache.CacheState
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
      // todo - check that expiration timeout grater then refresh timeout and wait less or equals then difference between them
      cacheState <- Stream.eval(CacheState.initial[F])
      module = new Module[F](config, cacheState, blockingEC)
      _ <- Stream.eval(module.startAutoRefreshableCache)
      _ <- BlazeServerBuilder[F]
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()
}
