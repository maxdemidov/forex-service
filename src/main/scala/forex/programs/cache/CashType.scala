package forex.programs.cache

import forex.domain.Rate

object CashType {
  type RatesMap = Map[Rate.Pair, Rate]
}
