package tm.support.logback

import java.net.URI

import tm.support.logback.MonitoringConfig.TelegramConfig

case class MonitoringConfig(telegramAlert: TelegramConfig)

object MonitoringConfig {
  case class TelegramConfig(
      apiUrl: URI,
      chatId: String,
      enabled: Boolean,
    )
}
