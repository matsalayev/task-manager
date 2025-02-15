package tm.integrations.telegram.requests

import io.circe.Json
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import sttp.model.Method
import tm.support.sttp.SttpRequest

@ConfiguredJsonCodec
case class DeleteMessage(
    chatId: Long,
    messageId: Long,
  )

object DeleteMessage {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def sttpRequest: SttpRequest[DeleteMessage, Json] =
    new SttpRequest[DeleteMessage, Json] {
      val method: Method = Method.POST
      override def path: Path = r => s"deleteMessage"
      def body: Body = jsonBody
    }
}
