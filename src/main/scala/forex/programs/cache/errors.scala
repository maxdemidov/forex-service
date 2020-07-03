package forex.programs.cache

import forex.services.rates.errors.{Error => RatesServiceError}

object errors {

  sealed trait Error
  object Error {
    final case class CacheRefreshRequestFailed(msg: String) extends Error
    final case class CacheRefreshParseResponseFailed(msg: String) extends Error
    final case class CacheRefreshOneFrameError(msg: String) extends Error
    final case class CacheRefreshTimeoutExceeded(msg: String) extends Error
  }

  def toCacheError(error: RatesServiceError): Error = error match {
    case RatesServiceError.RequestFailed(msg) => Error.CacheRefreshRequestFailed(msg)
    case RatesServiceError.ParseResponseFailed(msg) => Error.CacheRefreshParseResponseFailed(msg)
    case RatesServiceError.OneFrameError(msg) => Error.CacheRefreshOneFrameError(msg)
  }
}
