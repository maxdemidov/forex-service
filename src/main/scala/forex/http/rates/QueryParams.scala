package forex.http.rates

import cats.implicits._
import forex.domain.Currency
import forex.http.rates.Protocol.GetApiRequest
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.{OptionalMultiQueryParamDecoderMatcher, QueryParamDecoderMatcher}

import scala.util.{Failure, Success, Try}

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Either[String, Currency]] =
    QueryParamDecoder[String].map(str => Try(Currency.fromString(str)) match {
      case Success(currency) => Right(currency)
      case Failure(_)        => Left(Messages.invalidCurrency(str))
    })

  object FromQueryParam extends QueryParamDecoderMatcher[Either[String, Currency]]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Either[String, Currency]]("to")

  private[http] implicit val currenciesQueryParam: QueryParamDecoder[Either[String, GetApiRequest]] =
    QueryParamDecoder[String].map(str =>
      if (str.length == 6) {
        str.sliding(3, 3).toList
          .map(str => Try(Currency.fromString(str)) match {
            case Success(currency) => Right(currency)
            case Failure(_)        => Left(Messages.invalidCurrency(str))
          })
          .traverse(_.toValidated).toEither
          .map(ft => GetApiRequest(ft.head, ft.tail.head))
      } else {
        Left(Messages.invalidPair(str))
      })

  object PairQueryParam extends OptionalMultiQueryParamDecoderMatcher[Either[String, GetApiRequest]]("pair")

  object Messages {

    def invalidCurrency(str: String) =
      s"Unsupported or incorrect currency [$str]"

    def invalidPair(str: String) =
      s"Wrong pair [$str], for example expected format for pair from USD to JPY should be USDJPY"
  }
}
