package tm.integrations.telegram

import java.net.URI

import cats.Applicative
import cats.effect.Sync
import cats.implicits.none
import cats.implicits.toFunctorOps
import org.typelevel.log4cats.Logger
import sttp.client3.HttpURLConnectionBackend
import sttp.client3.asByteArray
import sttp.client3.basicRequest
import sttp.model.Uri

import tm.integrations.telegram.domain.GetFileResponse
import tm.integrations.telegram.domain.MessageEntity
import tm.integrations.telegram.domain.ReplyMarkup
import tm.integrations.telegram.requests.AnswerCallbackQuery
import tm.integrations.telegram.requests.DeleteMessage
import tm.integrations.telegram.requests.EditMessageCaption
import tm.integrations.telegram.requests.EditMessageReplyMarkup
import tm.integrations.telegram.requests.EditMessageText
import tm.integrations.telegram.requests.GetFile
import tm.integrations.telegram.requests.SendMessage
import tm.integrations.telegram.requests.SendPhoto
import tm.support.sttp.SttpBackends
import tm.support.sttp.SttpClient
import tm.support.sttp.SttpClientAuth

trait TelegramClient[F[_]] {
  def sendMessage(
      chatId: Long,
      text: String,
      replyMarkup: Option[ReplyMarkup] = None,
      entities: Option[List[MessageEntity]] = None,
    ): F[Unit]

  def sendPhoto(
      chatId: Long,
      photo: Array[Byte],
      caption: Option[String] = None,
      replyMarkup: Option[ReplyMarkup] = None,
      entities: Option[List[MessageEntity]] = None,
    ): F[Unit]

  def editMessageReplyMarkup(
      chatId: Long,
      messageId: Long,
      replyMarkup: Option[ReplyMarkup],
    ): F[Unit]

  def answerCallbackQuery(
      callbackQueryId: String,
      text: String,
      showAlert: Boolean,
      cache_time: Int,
    ): F[Unit]

  def editMessageText(
      chatId: Long,
      messageId: Long,
      text: String,
    ): F[Unit]

  def editMessageCaption(
      chatId: Long,
      messageId: Long,
      caption: String,
      captionEntities: Option[List[MessageEntity]],
    ): F[Unit]

  def getFile(fileId: String): F[GetFileResponse]

  def downloadFile(filePath: String): F[Option[Array[Byte]]]

  def deleteMessage(chatId: Long, messageId: Long): F[Unit]
}

object TelegramClient {
  def make[F[_]: Sync: Logger: SttpBackends.Simple](config: TelegramBotsConfig): TelegramClient[F] =
    if (config.enabled)
      new TelegramClientImpl[F](config)
    else
      new NoOpTelegramClientImpl[F]

  private class TelegramClientImpl[F[_]: Sync: SttpBackends.Simple](config: TelegramBotsConfig)
      extends TelegramClient[F] {
    private lazy val client: SttpClient.CirceJson[F] =
      SttpClient.circeJson(
        config.apiUrl,
        SttpClientAuth.noAuth,
      )

    override def sendMessage(
        chatId: Long,
        text: String,
        reply_markup: Option[ReplyMarkup] = None,
        entities: Option[List[MessageEntity]] = None,
      ): F[Unit] =
      client
        .request(
          SendMessage(chatId, text, reply_markup, entities)
        )
        .void

    override def sendPhoto(
        chatId: Long,
        photo: Array[Byte],
        caption: Option[String],
        replyMarkup: Option[ReplyMarkup],
        entities: Option[List[MessageEntity]] = None,
      ): F[Unit] =
      client
        .request(
          SendPhoto(
            chatId = chatId,
            photo = photo,
            caption = caption,
            replyMarkup = replyMarkup,
            captionEntities = entities,
          )
        )
        .void

    override def editMessageReplyMarkup(
        chatId: Long,
        messageId: Long,
        replyMarkup: Option[ReplyMarkup],
      ): F[Unit] =
      client.request(EditMessageReplyMarkup(chatId, messageId, replyMarkup)).void

    override def editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
      ): F[Unit] =
      client.request(EditMessageText(chatId, messageId, text)).void

    override def editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        captionEntities: Option[List[MessageEntity]],
      ): F[Unit] =
      client.request(EditMessageCaption(chatId, messageId, caption, captionEntities)).void

    override def getFile(fileId: String): F[GetFileResponse] =
      client.request(GetFile(fileId))

    override def downloadFile(filePath: String): F[Option[Array[Byte]]] =
      Sync[F].delay {
        val path = URI.create(s"${config.fileApiUrl.toString}/$filePath")
        val url = Uri(path)
        println(url)
        val backend = HttpURLConnectionBackend()
        val response = basicRequest.get(url).response(asByteArray).send(backend)

        response.body.toOption
      }

    override def deleteMessage(chatId: Long, messageId: Long): F[Unit] =
      client.request(DeleteMessage(chatId, messageId)).void

    override def answerCallbackQuery(
        callbackQueryId: String,
        text: String,
        showAlert: Boolean,
        cache_time: Int,
      ): F[Unit] =
      client.request(AnswerCallbackQuery(callbackQueryId, text, showAlert, cache_time)).void
  }

  private class NoOpTelegramClientImpl[F[_]: Applicative](implicit logger: Logger[F])
      extends TelegramClient[F] {
    override def sendMessage(
        chatId: Long,
        text: String,
        reply_markup: Option[ReplyMarkup] = None,
        entities: Option[List[MessageEntity]] = None,
      ): F[Unit] =
      logger.info(s"Telegram message sent [$chatId]: $text")

    override def sendPhoto(
        chatId: Long,
        photo: Array[Byte],
        caption: Option[String] = None,
        replyMarkup: Option[ReplyMarkup] = None,
        entities: Option[List[MessageEntity]] = None,
      ): F[Unit] =
      logger.info(s"Telegram sent photo to [$chatId]")

    override def editMessageReplyMarkup(
        chatId: Long,
        messageId: Long,
        replyMarkup: Option[ReplyMarkup],
      ): F[Unit] = logger.info(s"Telegram edit button to [$chatId]")

    override def editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
      ): F[Unit] = logger.info(s"Telegram edit message to [$chatId]")

    override def editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        captionEntities: Option[List[MessageEntity]],
      ): F[Unit] = logger.info(s"Telegram edit message caption to [$chatId]")

    override def getFile(fileId: String): F[GetFileResponse] =
      logger.info(s"Telegram sent photo to [$fileId]").map(_ => GetFileResponse(ok = false, None))

    override def downloadFile(filePath: String): F[Option[Array[Byte]]] =
      logger.info(s"Telegram sent photo to [$filePath]").map(_ => none[Array[Byte]])

    override def deleteMessage(chatId: Long, messageId: Long): F[Unit] =
      logger.info(s"Telegram message deleted [$chatId]")

    override def answerCallbackQuery(
        callbackQueryId: String,
        text: String,
        showAlert: Boolean,
        cache_time: Int,
      ): F[Unit] =
      logger.info(s"Telegram answer callback query")
  }
}
