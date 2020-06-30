package forex.domain

case class Rate(
    pair: Rate.Pair,
    bid: Bid,
    ask: Ask,
    price: Price,
    timestamp: Timestamp
)

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  ) extends Serializable
}
