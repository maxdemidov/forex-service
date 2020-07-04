package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    frame: FrameConfig,
    cache: CacheConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    requestTimeout: FiniteDuration
)

case class FrameConfig(
    host: String,
    port: Int,
    token: String
)

case class CacheConfig(
    expirationTimeout: FiniteDuration,
    refreshTimeout: FiniteDuration,
    waitTimeout: FiniteDuration
)
