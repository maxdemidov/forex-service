package forex.programs.rates

import forex.domain.Currency
//import io.circe.Encoder
//import io.circe.generic.semiauto.deriveEncoder

object Protocol {

  final case class GetRatesRequest(
      from: Currency,
      to: Currency
  )


//  final case class ErrorResponse(message: String)
//
//  implicit val framePairDecoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
}
