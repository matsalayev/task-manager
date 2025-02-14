package tm.integrations.telegram.requests

import io.circe.Json
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import sttp.model.Method

import tm.support.sttp.SttpRequest

@ConfiguredJsonCodec
case class EditMessageText(
    chatId: Long,
    messageId: Long,
    text: String,
  )

object EditMessageText {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def sttpRequest: SttpRequest[EditMessageText, Json] =
    new SttpRequest[EditMessageText, Json] {
      val method: Method = Method.POST
      override def path: Path = r => s"editMessageText"
      def body: Body = jsonBody
    }
}
