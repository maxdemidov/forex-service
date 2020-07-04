package forex.services.cache

object errors {

  sealed trait Error
  object Error {
    final case class StateLookupFailed(msg: String) extends Error
  }
}
