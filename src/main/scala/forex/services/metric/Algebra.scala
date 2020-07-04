package forex.services.metric

trait Algebra[F[_]] {
  def add(call: TimedCall): F[Unit]
  def clean(now: Long): F[Unit]
}
