package forex.services.rates

object errors {

  sealed trait Error
  object Error {
    final case class RequestFailed(msg: String) extends Error
    final case class ParseResponseFailed(msg: String) extends Error
    final case class OneFrameError(msg: String) extends Error
  }
}
