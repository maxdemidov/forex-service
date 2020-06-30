package forex.domain

case class Bid(value: BigDecimal) extends AnyVal

object Bid {
  def apply(value: Integer): Price =
    Price(BigDecimal(value))
}
