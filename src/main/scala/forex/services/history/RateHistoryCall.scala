package forex.services.history

import forex.domain.Rate

case class RateHistoryCall(callTime: Long,
                           description: String,
                           rates: Option[List[Rate]])