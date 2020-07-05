package forex.services.rates.interpreters

import forex.services.rates.Algebra
import cats.Applicative
import forex.domain.{Ask, Bid, Price, Rate, Timestamp}
import forex.services.rates.errors._
import cats.syntax.either._
import cats.syntax.applicative._
import forex.domain.Currency.{EUR, USD}
import forex.domain.RateTypes.RatesList

// todo - remove
class OneFrameDummy[F[_]: Applicative] extends Algebra[F] {

  override def get(pairs: List[Rate.Pair]): F[Error Either RatesList] = {
    val pair = Rate.Pair(USD, EUR)
    val rate = Rate(pair, Bid(BigDecimal(100)), Ask(BigDecimal(100)), Price(BigDecimal(100)), Timestamp.now)
    List(rate).asRight[Error].pure[F]
  }
}
