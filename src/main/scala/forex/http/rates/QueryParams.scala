package forex.http.rates

import forex.domain.Currency
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher

import scala.util.{Failure, Success, Try}

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Either[String, Currency]] =
    QueryParamDecoder[String].map(str => Try(Currency.fromString(str)) match {
      case Success(currency) => Right(currency)
      case Failure(t)        => Left(s"Unsupported or incorrect currency [$str]") // todo - add warn log with t.getMessage
    })

  object FromQueryParam extends QueryParamDecoderMatcher[Either[String, Currency]]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Either[String, Currency]]("to")
}
