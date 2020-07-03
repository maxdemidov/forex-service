package forex.services.rates

import io.circe.generic.extras.decoding.{EnumerationDecoder, UnwrappedDecoder}
import io.circe.generic.extras.encoding.{EnumerationEncoder, UnwrappedEncoder}
import io.circe.{Decoder, Encoder}

package object frame {

  implicit def valueClassEncoder[A: UnwrappedEncoder]: Encoder[A] = implicitly
  implicit def valueClassDecoder[A: UnwrappedDecoder]: Decoder[A] = implicitly

  implicit def enumEncoder[A: EnumerationEncoder]: Encoder[A] = implicitly
  implicit def enumDecoder[A: EnumerationDecoder]: Decoder[A] = implicitly
}
