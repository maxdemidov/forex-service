package forex.programs.cache

import forex.programs.cache.CashType.RatesMap

case class CacheState(timestamp: Long, rates: RatesMap)
