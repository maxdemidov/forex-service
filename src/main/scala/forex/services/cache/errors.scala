package forex.services.cache

object errors {

  sealed trait Error
  object Error {
    final case class RatesLookupFailed(msg: String) extends Error
  }
}
