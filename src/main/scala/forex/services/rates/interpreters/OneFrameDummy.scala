package forex.services.rates.interpreters

import forex.services.rates.Algebra
import cats.Applicative
import cats.effect.concurrent.MVar
import forex.domain.{Ask, Bid, Price, Rate, Timestamp}
import forex.services.rates.errors._
import cats.syntax.either._
import cats.syntax.applicative._
import forex.programs.rates.CacheState

// todo - remove
class OneFrameDummy[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair,
                   state: F[MVar[F, Option[CacheState]]]): F[Error Either Rate] =
    Rate(pair, Bid(BigDecimal(100)), Ask(BigDecimal(100)), Price(BigDecimal(100)), Timestamp.now)
      .asRight[Error].pure[F]
}
