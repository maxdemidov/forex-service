package forex.domain

import scala.annotation.tailrec

object RateTypes {

  type RatesMap = Map[Rate.Pair, Rate]
  type RatesList = List[Rate]

  val emptyRatesMap: RatesMap = Map[Rate.Pair, Rate]()

  implicit class RatesListOpt(val ratesList: RatesList) extends AnyVal {
    def asMap: RatesMap =
      convertToMap(ratesList, emptyRatesMap)

    @tailrec
    private def convertToMap(frameRates: RatesList, map: RatesMap): RatesMap = {
      frameRates match {
        case rate :: otherRates => convertToMap(otherRates, map + (rate.pair -> rate))
        case Nil                => map
      }
    }
  }
}
