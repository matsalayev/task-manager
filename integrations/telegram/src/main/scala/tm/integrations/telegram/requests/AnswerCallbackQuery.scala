package tm.integrations.telegram.requests

import io.circe.Json
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import sttp.model.Method

import tm.integrations.telegram.domain.MessageEntity
import tm.integrations.telegram.domain.ReplyMarkup
import tm.support.sttp.SttpRequest

@ConfiguredJsonCodec
case class AnswerCallbackQuery(
    callbackQueryId: String,
    text: String,
    showAlert: Boolean,
    cache_time: Int,
  )

object AnswerCallbackQuery {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit def sttpRequest: SttpRequest[AnswerCallbackQuery, Json] =
    new SttpRequest[AnswerCallbackQuery, Json] {
      val method: Method = Method.POST
      override def path: Path = r => s"answerCallbackQuery"
      def body: Body = jsonBody
    }
}
