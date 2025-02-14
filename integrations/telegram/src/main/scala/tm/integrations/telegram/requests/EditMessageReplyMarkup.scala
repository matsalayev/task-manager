package tm.integrations.telegram.requests

import io.circe.Json
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import sttp.model.Method

import tm.integrations.telegram.domain.ReplyMarkup
import tm.support.sttp.SttpRequest

@ConfiguredJsonCodec
case class EditMessageReplyMarkup(
    chatId: Long,
    messageId: Long,
    replyMarkup: Option[ReplyMarkup],
  )

object EditMessageReplyMarkup {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def sttpRequest: SttpRequest[EditMessageReplyMarkup, Json] =
    new SttpRequest[EditMessageReplyMarkup, Json] {
      val method: Method = Method.POST
      override def path: Path = r => s"editMessageReplyMarkup"
      def body: Body = jsonBody
    }
}
