package forex.services.history

trait Algebra[F[_]] {
  def add(call: RateHistoryCall): F[Unit]
  def clean(now: Long): F[Unit]
  def getAll: F[List[RateHistoryCall]]
}
