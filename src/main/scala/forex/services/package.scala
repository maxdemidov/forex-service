package forex

package object services {

  type RatesService[F[_]] = rates.Algebra[F]
  final val RatesServices = rates.Interpreters

  type RatesCacheService[F[_]] = cache.Algebra[F]

  type CallsHistoryService[F[_]] = metric.Algebra[F]
  final val CallsHistoryServices = metric.Interpreters
}
