package forex.services.cache

import forex.domain.Rate

object errors {

  sealed trait Error
  object Error {
    final case class RateNotFound(msg: String) extends Error
    final case class RateInvalidFound(msg: String) extends Error
  }

  object Messages {
    def invalidRateMessage(pair: Rate.Pair, rate: Rate) = s"Found invalid rate for pair = [$pair], rate = [$rate]"
    def foundRateMessage(pair: Rate.Pair, rate: Rate) = s"Found rate for pair = [$pair], rate = [$rate]"
    def notFoundRateMessage(pair: Rate.Pair) = s"No found rate for pair = [$pair]"
  }
}
