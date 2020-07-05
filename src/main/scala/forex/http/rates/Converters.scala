package forex.http.rates

import forex.programs.rates.Protocol.{GetRatesRequest, GetRatesResponse}

object Converters {
  import Protocol._

  private[rates] implicit class GetApiResponseOps(val getRatesResponse: GetRatesResponse) extends AnyVal {
    def asGetApiResponse: GetApiResponse =
      GetApiResponse(
        from = getRatesResponse.from,
        to = getRatesResponse.to,
        bid = getRatesResponse.bid,
        ask = getRatesResponse.ask,
        price = getRatesResponse.price,
        timestamp = getRatesResponse.timestamp
      )
  }

  private[rates] implicit class GetRatesRequestOps(val getApiRequest: GetApiRequest) extends AnyVal {
    def asGetRatesRequest: GetRatesRequest =
      GetRatesRequest(
        from = getApiRequest.from,
        to = getApiRequest.to
      )
  }
}
