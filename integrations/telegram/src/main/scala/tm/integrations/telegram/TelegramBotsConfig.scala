package tm.integrations.telegram

import java.net.URI

import eu.timepit.refined.types.string.NonEmptyString

case class TelegramBotsConfig(
    enabled: Boolean,
    apiUrl: URI,
    fileApiUrl: URI,
    webhookSecret: NonEmptyString,
  )
