package forex.programs.cache

import forex.domain.types.RateTypes.RatesMap

trait Algebra[F[_]] {
  def startAutoRefreshableCache(): F[Unit]
  def obtainCachedMap: F[RatesMap]
}
