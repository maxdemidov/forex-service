package forex.common.datetime

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZoneOffset}

object DateTimeConverters {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def toDateTimeFormat(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.of(ZoneOffset.UTC.getId)).toLocalDateTime.format(formatter)
}
