package forex.services.metric

// todo - ???
object errors {

  sealed trait Error
  object Error {
    final case class SetToHistoryFailed(msg: String) extends Error
    final case class GetFromHistoryFailed(msg: String) extends Error
  }
}
