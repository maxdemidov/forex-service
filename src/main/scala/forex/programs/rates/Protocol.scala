package forex.programs.rates

import forex.domain.{Ask, Bid, Currency, Price, Timestamp}

object Protocol {

  final case class GetRatesRequest(
      from: Currency,
      to: Currency
  )

  final case class GetRatesResponse(
      from: Currency,
      to: Currency,
      bid: Bid,
      ask: Ask,
      price: Price,
      timestamp: Timestamp
  )
}
