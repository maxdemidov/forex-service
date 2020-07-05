package forex.programs.rates

import forex.services.cache.errors.{ Error => RatesServiceError }

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.RatesLookupFailed(msg) => Error.RateLookupFailed(msg)
  }
}
