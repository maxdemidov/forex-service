package forex.services.rates.frame

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import forex.domain.Currency
import forex.services.rates.frame.Protocol.FrameError.ErrorMessage
import forex.services.rates.frame.Protocol.FrameRate.{RateDateTime, RateValue}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import scala.util.{Failure, Success, Try}

object Protocol {

  case class FrameRate(from: Currency,
                       to: Currency,
                       bid: RateValue,
                       ask: RateValue,
                       price: RateValue,
                       time_stamp: RateDateTime) // todo - make camel case

  case object FrameRate {

    case class RateValue(value: BigDecimal) extends AnyVal
    case object RateValue {
      def apply(value: Double): RateValue =
        RateValue(BigDecimal(value))
    }

    case class RateDateTime(dateTime: LocalDateTime) extends AnyVal
  }

  implicit val framePairDecoder: Decoder[FrameRate] = deriveDecoder[FrameRate]


  case class FrameError(error: ErrorMessage)

  case object FrameError {
    case class ErrorMessage(message: String) extends AnyVal
  }

  implicit val frameErrorDecoder: Decoder[FrameError] = deriveDecoder[FrameError]


  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") // todo - there is some problems in case .1Z at the end
  def parseToEither(str: String): Either[String, LocalDateTime] =
    Try(LocalDateTime.parse(str, formatter)) match {
      case Success(ldt) => Right(ldt)
      case Failure(t) => Left(t.getMessage)
    }

  implicit val rateDateimeDecoder: Decoder[LocalDateTime] = Decoder.decodeString.emap[LocalDateTime](parseToEither)
}
