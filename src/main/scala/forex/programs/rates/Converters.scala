package forex.programs.rates

import forex.domain.Rate
import forex.programs.rates.Protocol.{GetRatesRequest, GetRatesResponse}

object Converters {

  private[rates] implicit class GetRatesResponseOps(val rate: Rate) extends AnyVal {
    def asGetRatesResponse: GetRatesResponse =
      GetRatesResponse(
        from = rate.pair.from,
        to = rate.pair.to,
        bid = rate.bid,
        ask = rate.ask,
        price = rate.price,
        timestamp = rate.timestamp
      )
  }

  private[rates] implicit class PairOps(val getRatesRequest: GetRatesRequest) extends AnyVal {
    def asPair: Rate.Pair =
      Rate.Pair(
        from = getRatesRequest.from,
        to = getRatesRequest.to
      )
  }
}
