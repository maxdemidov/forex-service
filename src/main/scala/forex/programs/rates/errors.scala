package forex.programs.rates

import forex.services.cache.errors.{ Error => RatesServiceError }

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateNotFound(msg: String) extends Error
    final case class RateInvalidFound(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.RateNotFound(msg) => Error.RateNotFound(msg)
    case RatesServiceError.RateInvalidFound(msg) => Error.RateInvalidFound(msg)
  }
}
