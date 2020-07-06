package forex.services.history.model

import forex.domain.Rate

case class RateHistoryCall(callTime: Long,
                           description: String,
                           rates: Option[List[Rate]])
