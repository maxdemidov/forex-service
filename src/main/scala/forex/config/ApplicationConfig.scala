package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    frame: FrameConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    requestTimeout: FiniteDuration
)

case class FrameConfig(
    host: String,
    port: Int,
    refreshTimeout: FiniteDuration
)
