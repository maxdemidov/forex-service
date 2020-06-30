package forex.domain

import java.time.{LocalDateTime, OffsetDateTime, ZoneId}

case class Timestamp(value: OffsetDateTime) extends AnyVal

object Timestamp {
  def now: Timestamp =
    Timestamp(OffsetDateTime.now)

  def from(ldt: LocalDateTime): Timestamp =
    Timestamp(ldt.atOffset(ZoneId.of(ZoneId.systemDefault().getId).getRules.getOffset(ldt)))
}
