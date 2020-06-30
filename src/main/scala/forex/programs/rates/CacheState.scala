package forex.programs.rates

import java.time.LocalDateTime

import forex.domain.Rate

case class CacheState(timestamp: LocalDateTime, rates: Map[Rate.Pair, Rate])
