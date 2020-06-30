package forex.domain

case class Ask(value: BigDecimal) extends AnyVal

object Ask {
  def apply(value: Integer): Price =
    Price(BigDecimal(value))
}
