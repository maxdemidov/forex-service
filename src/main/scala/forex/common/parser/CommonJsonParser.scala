package forex.common.parser

import io.circe.Decoder
import io.circe.parser.parse

trait CommonJsonParser {

  def parseTo[T](body: String)(implicit decoder: Decoder[T]): Either[String, T] = {
    parse(body).map(json => json.as[T]) match {
      case Right(dr) => dr match {
        case Right(parsed) => Right(parsed)
        case Left(df) => Left(s"Decoding failure message: ${df.message}")
      }
      case Left(pf) => Left(s"Parsing failure message: ${pf.message}")
    }
  }
}
