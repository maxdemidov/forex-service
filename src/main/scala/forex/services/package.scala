package forex

package object services {

  type RatesService[F[_]] = rates.Algebra[F]
  final val RatesServices = rates.Interpreters

  type RatesCacheService[F[_]] = cache.Algebra[F]

  type CallsHistoryService[F[_]] = history.Algebra[F]
  final val CallsHistoryServices = history.Interpreters
}
