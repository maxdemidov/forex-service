package forex.programs.rates

import forex.services.state.errors.{ Error => RatesServiceError }
//import forex.programs.cache.errors.{ Error => CacheError }

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error
//    final case class CacheRefreshFailed(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.StateLookupFailed(msg) => Error.RateLookupFailed(msg)
  }

//  def toProgramError(error: CacheError): Error = error match {
//    case CacheError.CacheRefreshParsingFailed(msg) => Error.CacheRefreshFailed(msg)
//    case CacheError.CacheRefreshRequestFailed(msg) => Error.CacheRefreshFailed(msg)
//    case CacheError.CacheRefreshTimeoutExceeded(msg) => Error.CacheRefreshFailed(msg)
//  }
}
