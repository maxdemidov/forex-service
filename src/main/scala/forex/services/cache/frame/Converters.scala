package forex.services.cache.frame

import forex.domain.{Ask, Bid, Price, Rate, Timestamp}
import forex.services.cache.frame.Protocol.FrameRate

object Converters {

  implicit class FrameRateOps(val frameRate: FrameRate) extends AnyVal {
    def asRate: Rate =
      Rate(
        Rate.Pair(
          from = frameRate.from,
          to = frameRate.to),
        bid = Bid(frameRate.bid.value),
        ask = Ask(frameRate.ask.value),
        price = Price(frameRate.price.value),
        timestamp = Timestamp.from(frameRate.time_stamp.dateTime)
      )
  }
}
