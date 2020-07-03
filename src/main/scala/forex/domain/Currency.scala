package forex.domain

import cats.Show
import forex.common.reflect.SubtypeToList

sealed trait Currency extends Serializable

object Currency {
  case object AUD extends Currency
  case object CAD extends Currency
  case object CHF extends Currency
  case object EUR extends Currency
  case object GBP extends Currency
  case object NZD extends Currency
  case object JPY extends Currency
  case object SGD extends Currency
  case object USD extends Currency

  implicit val show: Show[Currency] = Show.show {
    case AUD => "AUD"
    case CAD => "CAD"
    case CHF => "CHF"
    case EUR => "EUR"
    case GBP => "GBP"
    case NZD => "NZD"
    case JPY => "JPY"
    case SGD => "SGD"
    case USD => "USD"
  }

  def fromString(s: String): Currency = s.toUpperCase match {
    case "AUD" => AUD
    case "CAD" => CAD
    case "CHF" => CHF
    case "EUR" => EUR
    case "GBP" => GBP
    case "NZD" => NZD
    case "JPY" => JPY
    case "SGD" => SGD
    case "USD" => USD
  }

  def isEquals(currency1: Currency, currency2: Currency): Boolean =
    show.show(currency1) == show.show(currency2)

  val allCurrencies: List[Currency] = SubtypeToList.fromKnownSubtypes[Currency]

  val allPairs: List[Rate.Pair] = allCurrencies.flatMap(cf => {
    allCurrencies.filterNot(isEquals(_, cf)).map(ct => Rate.Pair(from = cf, to = ct))
  })
}
