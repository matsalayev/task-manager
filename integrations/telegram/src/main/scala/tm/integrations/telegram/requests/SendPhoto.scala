package tm.integrations.telegram.requests

import cats.implicits.catsSyntaxOptionId
import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import sttp.model.Method

import tm.integrations.telegram.domain.MessageEntity
import tm.integrations.telegram.domain.ReplyMarkup
import tm.support.sttp.SttpRequest
import tm.syntax.all.genericSyntaxGenericTypeOps

@ConfiguredJsonCodec
case class SendPhoto(
    chatId: Long,
    photo: Array[Byte],
    caption: Option[String],
    replyMarkup: Option[ReplyMarkup],
    captionEntities: Option[List[MessageEntity]],
  )

object SendPhoto {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def sttpRequest: SttpRequest[SendPhoto, Json] =
    new SttpRequest[SendPhoto, Json] {
      val method: Method = Method.POST
      override def path: Path = r => "sendPhoto"
      def body: Body = multipartBody(
        req =>
          List(
            ("chat_id" -> req.chatId.toString).some,
            req.caption.map(c => "caption" -> c),
            req.replyMarkup.map(c => "reply_markup" -> c.toJson),
            req.captionEntities.map(c => "caption_entities" -> c.toJson),
          ).flatten.toMap,
        req => Map("photo" -> req.photo),
      )
    }
}
