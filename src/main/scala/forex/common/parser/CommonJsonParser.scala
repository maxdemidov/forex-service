package forex.common.parser

import io.circe.Decoder
import io.circe.parser.parse

trait CommonJsonParser {

  def parseTo[T](body: String)(implicit decoder: Decoder[T]): Either[String, T] = {
    parse(body).map(json => json.as[T]) match {
      case Left(pf) => Left(s"Parsing failure message: ${pf.message}")
      case Right(dr) => dr match {
        case Left(df) => Left(s"Decoder failure message: ${df.message}")
        case Right(parsed) => Right(parsed)
      }
    }
  }
}
