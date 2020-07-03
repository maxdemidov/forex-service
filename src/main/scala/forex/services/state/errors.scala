package forex.services.state

object errors {

  sealed trait Error
  object Error {
    final case class StateLookupFailed(msg: String) extends Error
  }
}
