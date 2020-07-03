package forex

package object services {

  type RatesService[F[_]] = rates.Algebra[F]
  final val RatesServices = rates.Interpreters

  type StateService[F[_]] = state.Algebra[F]
//  final val StateServices = state.Interpreters
}
