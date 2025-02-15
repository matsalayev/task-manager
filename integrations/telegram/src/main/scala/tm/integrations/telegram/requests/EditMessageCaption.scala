package tm.integrations.telegram.requests

import io.circe.Json
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import sttp.model.Method
import tm.integrations.telegram.domain.MessageEntity
import tm.support.sttp.SttpRequest

@ConfiguredJsonCodec
case class EditMessageCaption(
    chatId: Long,
    messageId: Long,
    caption: String,
    captionEntities: Option[List[MessageEntity]],
  )

object EditMessageCaption {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def sttpRequest: SttpRequest[EditMessageCaption, Json] =
    new SttpRequest[EditMessageCaption, Json] {
      val method: Method = Method.POST
      override def path: Path = r => s"editMessageCaption"
      def body: Body = jsonBody
    }
}
