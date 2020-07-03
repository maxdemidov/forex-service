package forex.services.state

//import cats.Applicative
//import cats.effect.Concurrent
//import cats.effect.concurrent.Ref
//import forex.config.FrameConfig
//import forex.programs.cache.CacheState
//import forex.services.state.interpreters.OneFrameCache

//object Interpreters {
//  def live[F[_]: Applicative: Concurrent](config: FrameConfig,
//                                          state: Ref[F, Option[CacheState]]): Algebra[F] =
//    new OneFrameCache[F](config, state)
//}
