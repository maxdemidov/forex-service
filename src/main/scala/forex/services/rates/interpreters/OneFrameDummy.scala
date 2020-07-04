package forex.services.rates.interpreters

import forex.services.rates.Algebra
import cats.Applicative
import forex.domain.{Ask, Bid, Price, Rate, Timestamp}
import forex.services.rates.errors._
import cats.syntax.either._
import cats.syntax.applicative._
import forex.domain.Currency.{EUR, USD}
import forex.programs.cache.RatesCacheRef.RatesMap

// todo - remove
class OneFrameDummy[F[_]: Applicative] extends Algebra[F] {

  override def refresh(pairs: List[Rate.Pair]): F[Error Either RatesMap] = {
    val pair = Rate.Pair(USD, EUR)
    val rate = Rate(pair, Bid(BigDecimal(100)), Ask(BigDecimal(100)), Price(BigDecimal(100)), Timestamp.now)
    val m = Map(pair -> rate)
    m.asRight[Error].pure[F]
//    Rate(pair, Bid(BigDecimal(100)), Ask(BigDecimal(100)), Price(BigDecimal(100)), Timestamp.now)
//      .asRight[Error].pure[F]
  }
}
