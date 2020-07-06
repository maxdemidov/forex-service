package forex.services.cache

object errors {

  sealed trait Error
  object Error {
    final case class RateNotFound(msg: String) extends Error
    final case class RateInvalidFound(msg: String) extends Error
  }
}
