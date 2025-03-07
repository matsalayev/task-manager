package tm.services

import java.time.LocalDate
import java.util.UUID

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import cats.Applicative
import cats.Monad
import cats.data.OptionT
import cats.effect.kernel.Sync
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.catsSyntaxOptionId
import cats.implicits.catsSyntaxTuple2Semigroupal
import cats.implicits.catsSyntaxTuple3Semigroupal
import cats.implicits.catsSyntaxTuple4Semigroupal
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import eu.timepit.refined.string.Regex
import eu.timepit.refined.types.string.NonEmptyString
import org.typelevel.log4cats.Logger

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.LocationId
import tm.domain.Person
import tm.domain.PersonId
import tm.domain.asset.Asset
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser
import tm.domain.corporate
import tm.domain.corporate.Corporate
import tm.domain.corporate.Location
import tm.domain.enums.Gender
import tm.domain.enums.Role
import tm.domain.telegram.BotUser
import tm.domain.telegram.CallbackQuery
import tm.domain.telegram.Contact
import tm.domain.telegram.Message
import tm.domain.telegram.PhotoSize
import tm.domain.telegram.Update
import tm.domain.telegram.User
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.integration.aws.s3.S3Client
import tm.integrations.telegram.TelegramClient
import tm.integrations.telegram.domain.InlineKeyboardButton
import tm.integrations.telegram.domain.KeyboardButton
import tm.integrations.telegram.domain.MessageEntity
import tm.integrations.telegram.domain.MessageEntityType
import tm.integrations.telegram.domain.ReplyMarkup.ReplyInlineKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardRemove
import tm.repositories.AssetsRepository
import tm.repositories.CorporationsRepository
import tm.repositories.EmployeeRepository
import tm.repositories.PeopleRepository
import tm.repositories.ProjectsRepository
import tm.repositories.TelegramRepository
import tm.repositories.UsersRepository
import tm.repositories.dto
import tm.support.redis.RedisClient
import tm.syntax.refined.commonSyntaxAutoRefineV
import tm.utils.ID
trait CorporateBotService[F[_]] {
  def telegramMessage(update: Update): F[Unit]
}

object CorporateBotService {
  def make[F[_]: Monad: GenUUID: Calendar: Sync](
      telegramClient: TelegramClient[F],
      telegramRepository: TelegramRepository[F],
      peopleRepository: PeopleRepository[F],
      usersRepository: UsersRepository[F],
      employeeRepository: EmployeeRepository[F],
      corporationsRepository: CorporationsRepository[F],
      projectsRepository: ProjectsRepository[F],
      assetsRepository: AssetsRepository[F],
      s3Client: S3Client[F],
      redis: RedisClient[F],
    )(implicit
      logger: Logger[F]
    ): CorporateBotService[F] = new CorporateBotService[F] {
    private def menuButtons(company: String): ReplyKeyboardMarkup =
      ReplyKeyboardMarkup(
        List(
          List(
            KeyboardButton(s"$company \uD83C\uDFE2"),
            KeyboardButton("Xodimlar \uD83D\uDC65"),
          ),
          List(
            KeyboardButton("Loyihalar \uD83D\uDDC2"),
            KeyboardButton("Vazifalar \uD83D\uDCCB"),
            KeyboardButton("Monitoring \uD83D\uDCCA"),
          ),
          List(KeyboardButton("Sozlamalar ⚙\uFE0F")),
        )
      )

    private val taskButtons = ReplyKeyboardMarkup(
      List(
        List(KeyboardButton("Vazifa yaratish \uD83C\uDFAF"), KeyboardButton("Taglar #\uFE0F⃣")),
        List(KeyboardButton("Menu \uD83C\uDFE0")),
      )
    )

    private val projectButtons = ReplyKeyboardMarkup(
      List(
        List(KeyboardButton("Loyiha qo'shish \uD83D\uDCDD")),
        List(KeyboardButton("Menu \uD83C\uDFE0")),
      )
    )

    private val settingButtons = ReplyKeyboardMarkup(
      List(
        List(KeyboardButton("Telegram kanal ulash \uD83D\uDCE2")),
        List(KeyboardButton("Hisob ma'lumotlarini yangilash ✏\uFE0F")),
        List(KeyboardButton("Menu \uD83C\uDFE0")),
      )
    )

    private val employeeButtons = ReplyKeyboardMarkup(
      List(
        List(KeyboardButton("Lavozimlar \uD83D\uDCBC"), KeyboardButton("Xodim qo'shish ➕")),
        List(KeyboardButton("Menu \uD83C\uDFE0")),
      )
    )

    private val companyButtons = ReplyKeyboardMarkup(
      List(
        List(KeyboardButton("E'lon yozish \uD83D\uDCCC")),
        List(KeyboardButton("Surat \uD83D\uDDBC"), KeyboardButton("Joylashuv \uD83D\uDDFA")),
        List(KeyboardButton("Menu \uD83C\uDFE0")),
      )
    )

    override def telegramMessage(update: Update): F[Unit] =
      update match {
        case Update(_, Some(message), _) => handleMessage(message)
        case Update(_, _, Some(callbackQuery)) => handleCallbackQuery(callbackQuery)
        case _ => logger.info("unknown update type")
      }

    private def handleMessage(message: Message): F[Unit] =
      message match {
        case Message(_, Some(user), Some(text), None, None, None, None, None) =>
          handleTextMessage(user, text)
        case Message(_, Some(user), None, Some(contact), None, None, None, None) =>
          handleContactMessage(user, contact)
        case Message(_, Some(user), None, None, Some(photos), _, mediaGroupId, None) =>
          handlePhotoMessage(user, photos.maxBy(_.width), mediaGroupId)
        case Message(_, Some(user), None, None, None, None, None, Some(location)) =>
          handleLocationMessage(user, location.latitude, location.longitude)
        case _ => logger.info("undefined behaviour for customer bot")
      }

    private def handleTextMessage(user: User, text: String): F[Unit] = {
      val regexFullName = """([\p{L}]+)\s+([\p{L}]+)""".r
      val regexDateOfBirth = """((19|20)\d{2}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01]))""".r
      val regexDocumentNumber = """(^[A-Z]{2}\d{7})""".r
      val regexPinfl = """(\d{14})""".r
      val regexCompanyName = """'([^']*)'""".r

      text match {
        case "/start" => sendContactRequest(user.id)
        case company if company.contains("🏢") => sendCompanySetting(user.id)
        case "Menu \uD83C\uDFE0" => sendMenu(user.id)
        case "Xodimlar \uD83D\uDC65" => sendEmployees(user.id)
        case "Loyihalar \uD83D\uDDC2" => sendProjects(user.id)
        case "Vazifalar \uD83D\uDCCB" => sendTasks(user.id)
        case "Monitoring \uD83D\uDCCA" => sendMonitoring(user.id)
        case "Sozlamalar ⚙\uFE0F" => sendSettings(user.id)
        case regexFullName(firstName, lastName) =>
          redis.get(user.id.toString + "+phone").flatMap {
            case Some(_) =>
              for {
                today <- Calendar[F].currentDate
                msg = s"Ism: $firstName\nFamiliya: $lastName "
                ask = s"Iltimos tug'ilgan kuningizni quyidagi formatda yozib yuboring: "
                example = s"$today "
                _ <- telegramClient.sendMessage(
                  user.id,
                  s"$msg\n\n$ask\n\n$example",
                  entities = List(
                    MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length),
                    MessageEntity(
                      MessageEntityType.Code,
                      msg.length + ask.length + 3,
                      example.length,
                    ),
                  ).some,
                )
                _ <- redis.put(user.id.toString + "+full_name", text, 60.minute)
              } yield ()
            case None => Applicative[F].unit
          }

        case regexDateOfBirth(date, _*) =>
          (
            redis.get(user.id.toString + "+full_name"),
            redis.put(user.id.toString + "+date", date, 60.minute),
          ).tupled.flatMap {
            case (Some(fullName), _) =>
              val nameParts = fullName.split(' ')
              val msg =
                s"Ism: ${nameParts.head}\nFamiliya: ${nameParts.last}\nTug'ilgan kun: $date "
              val ask = "Iltimos hujjat raqamingizni yozib yuboring: "
              val example = "AC1234567 "
              telegramClient.sendMessage(
                user.id,
                s"$msg\n\n$ask\n\n$example",
                entities = List(
                  MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length),
                  MessageEntity(
                    MessageEntityType.Code,
                    msg.length + ask.length + 3,
                    example.length,
                  ),
                ).some,
              )
            case _ => Applicative[F].unit
          }

        case regexDocumentNumber(document) =>
          (
            redis.get(user.id.toString + "+full_name"),
            redis.get(user.id.toString + "+date"),
            redis.put(user.id.toString + "+document", document, 60.minute),
          ).tupled.flatMap {
            case (Some(fullName), Some(date), _) =>
              val nameParts = fullName.split(' ')
              val msg =
                s"Ism: ${nameParts.head}\nFamiliya: ${nameParts.last}\nTug'ilgan kun: $date\nHujjat raqami: $document "
              val ask = "Iltimos pinfl raqamingizni yozib yuboring: "
              val example = "56001234567890 "
              telegramClient.sendMessage(
                user.id,
                s"$msg\n\n$ask\n\n$example",
                entities = List(
                  MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length),
                  MessageEntity(
                    MessageEntityType.Code,
                    msg.length + ask.length + 3,
                    example.length,
                  ),
                ).some,
              )
            case _ => Applicative[F].unit
          }

        case regexPinfl(pinfl) =>
          (
            redis.get(user.id.toString + "+full_name"),
            redis.get(user.id.toString + "+date"),
            redis.get(user.id.toString + "+document"),
            redis.put(user.id.toString + "+pinfl", pinfl, 60.minute),
          ).tupled.flatMap {
            case (Some(fullName), Some(date), Some(document), _) =>
              val nameParts = fullName.split(' ')
              val msg =
                s"Ism: ${nameParts.head}\nFamiliya: ${nameParts.last}\nTug'ilgan kun: $date\nHujjat raqami: $document\nPinfl: $pinfl "
              val ask = "Iltimos suratingizni yuboring. "
              for {
                _ <- telegramClient.sendMessage(
                  user.id,
                  s"$msg\n\n$ask",
                  entities = List(
                    MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length)
                  ).some,
                )
                id <- ID.make[F, PersonId]
                now <- Calendar[F].currentZonedDateTime
                gender = if (fullName.last == 'a') Gender.Female else Gender.Male
                _ <- peopleRepository.create(
                  dto.Person(
                    id = id,
                    createdAt = now,
                    fullName = fullName,
                    gender = gender,
                    dateOfBirth = LocalDate.parse(date).some,
                    documentNumber = NonEmptyString.unsafeFrom(document).some,
                    pinflNumber = NonEmptyString.unsafeFrom(pinfl).some,
                    updatedAt = None,
                    deletedAt = None,
                  )
                )
                _ <- saveBotUser(user.id, id)
                _ <- redis.del(user.id.toString + "+full_name")
                _ <- redis.del(user.id.toString + "+date")
                _ <- redis.del(user.id.toString + "+document")
                _ <- redis.del(user.id.toString + "+pinfl")
              } yield ()
            case _ => Applicative[F].unit
          }

        case regexCompanyName(companyName) =>
          (
            redis.get(user.id.toString + "+phone"),
            redis.get(user.id.toString + "+photo"),
            redis.put(user.id.toString + "+companyName", companyName, 60.minute),
          ).tupled.flatMap {
            case (Some(_), Some(_), _) =>
              val msg = s"Kamponiya nomi: $companyName "
              val ask = "Iltimos kamponiyangiz manzilini yuboring. "
              telegramClient.sendMessage(
                user.id,
                s"$msg\n\n$ask",
                entities = List(
                  MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length)
                ).some,
              )
            case _ => Applicative[F].unit
          }

        case _ => logger.info("undefined behaviour for corporate bot")
      }
    }

    private def handleContactMessage(user: User, contact: Contact): F[Unit] =
      contact match {
        case Contact(phoneNumberStr, Some(userTelegramId)) if user.id == userTelegramId =>
          val phoneNumber: Phone =
            if (phoneNumberStr.startsWith("+")) phoneNumberStr else s"+$phoneNumberStr"

          usersRepository.findByPhone(phoneNumber).flatMap {
            case Some(corporateUser) =>
              (for {
                personOpt <- peopleRepository.findById(corporateUser.id)
                corporateOpt <- corporationsRepository.findById(corporateUser.corporateId)
                assetOpt <- corporateUser.assetId.traverse(assetsRepository.findAsset)
              } yield (personOpt, corporateOpt, assetOpt.flatten)).flatMap {
                case (Some(person), Some(corporate), Some(asset)) =>
                  sendUserInfo(user.id, corporateUser.role, person, corporate.name, asset)
                case _ => Applicative[F].unit
              }

            case None =>
              val msg = "Uzr, sizni foydalanuvchilar orasidan topa olmadik! "
              val ask =
                "Agar bot foydalanuvchisi sifatida ro'yxatdan o'tmoqchi bo'lsangiz, ism va familiyangizni yozib yuboring:"
              val example = "Ism Familiya "
              redis.put(user.id.toString + "+phone", phoneNumber.value, 60.minute) *>
                telegramClient.sendMessage(
                  user.id,
                  s"$msg\n\n$ask\n\n$example",
                  entities = List(
                    MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length),
                    MessageEntity(
                      MessageEntityType.Code,
                      msg.length + ask.length + 3,
                      example.length,
                    ),
                  ).some,
                  replyMarkup = ReplyKeyboardRemove().some,
                )
          }

        case _ => Applicative[F].unit
      }

    private def handleCallbackQuery(callbackQuery: CallbackQuery): F[Unit] =
      callbackQuery match {
        case CallbackQuery(Some(user), _, Some(message), Some(data)) =>
          telegramRepository
            .findByChatId(user.id)
            .flatMap(personIdOpt =>
              personIdOpt.fold(Applicative[F].unit) { personId =>
                (
                  redis.get(user.id.toString + "+phone"),
                  redis.get(user.id.toString + "+photo"),
                  redis.get(user.id.toString + "+companyName"),
                  redis.get(user.id.toString + "+location"),
                ).tupled.flatMap {
                  case (Some(phone), Some(photo), Some(companyName), Some(location)) =>
                    for {
                      id <- ID.make[F, CorporateId]
                      now <- Calendar[F].currentZonedDateTime
                      _ = println(phone, photo, companyName, location)
                      company = Corporate(
                        id = id,
                        createdAt = now,
                        name = companyName,
                        locationId = LocationId(UUID.fromString(location)),
                        photo = None,
                      )
                      _ <- corporationsRepository.create(company)
                      role = Role.withName(data.value)
                      _ <- usersRepository.createUser(
                        corporate.User(
                          id = personId,
                          role = role,
                          phone = phone,
                          assetId = AssetId(UUID.fromString(photo)).some,
                          corporateId = id,
                        )
                      )
                      _ <- telegramClient.deleteMessage(
                        user.id,
                        messageId = message.messageId,
                      )
                      person <- peopleRepository.findById(personId)
                      _ <- person.fold(Applicative[F].unit) { person =>
                        assetsRepository
                          .findAsset(AssetId(UUID.fromString(photo)))
                          .flatMap(assetOpt =>
                            assetOpt.fold(Applicative[F].unit) { asset =>
                              sendUserInfo(user.id, role, person, company.name, asset)
                            }
                          )
                      }
                      _ <- redis.del(user.id.toString + "+phone")
                      _ <- redis.del(user.id.toString + "+photo")
                      _ <- redis.del(user.id.toString + "+companyName")
                      _ <- redis.del(user.id.toString + "+location")
                    } yield ()
                  case _ => Applicative[F].unit
                }
              }
            )
        case _ => logger.warn("unknown callback query structure")
      }

    private def handleCallbackData(data: NonEmptyString): F[Unit] = {
      val regexData =
        """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})__(\d{2})__(\d{2})""".r
      val regexWeek =
        """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})__(\d{2})""".r
      val regexMonth = """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})""".r

      data.value match {
        case _ => logger.warn("unknown data type")
      }
    }

    private def handlePhotoMessage(
        user: User,
        photoSize: PhotoSize,
        mediaGroupId: Option[String],
      ): F[Unit] =
      telegramRepository
        .findByChatId(user.id)
        .flatMap(personIdOpt =>
          personIdOpt.fold(logger.info("Foydalanuvchi topilmadi")) { _ =>
            if (mediaGroupId.isDefined)
              telegramClient.sendMessage(
                user.id,
                s"Send only one photo per message",
              )
            else
              for {
                fileResponse <- telegramClient.getFile(photoSize.fileId)
                _ <- fileResponse.result.fold(logger.info("file topilmadi")) { file =>
                  telegramClient.downloadFile(file.filePath).flatMap { response =>
                    response.fold(logger.info("fayl yuklanmadi")) { bytes =>
                      for {
                        key <- genFileKey(file.filePath)
                        streamByte = fs2.Stream.emits[F, Byte](bytes)
                        _ <- streamByte.through(s3Client.putObject(key)).compile.drain
                        id <- ID.make[F, AssetId]
                        now <- Calendar[F].currentZonedDateTime
                        _ <- assetsRepository.create(
                          Asset(
                            id = id,
                            createdAt = now,
                            s3Key = key,
                            fileName = None,
                            contentType = None,
                          )
                        )
                        _ <- redis.put(user.id.toString + "+photo", id.toString, 60.minutes)
                        msg = "Yana bir nechta bosqich qoldi. "
                        ask = "Iltimos kamponiyangiz nomini '' ichida yozib yuboring: "
                        example = "'Kamponiya' "
                        _ <- telegramClient.sendMessage(
                          user.id,
                          s"$msg\n\n$ask\n\n$example",
                          entities = List(
                            MessageEntity(MessageEntityType.Italic, msg.length + 2, ask.length),
                            MessageEntity(
                              MessageEntityType.Code,
                              msg.length + ask.length + 3,
                              example.length,
                            ),
                          ).some,
                        )
                      } yield ()
                    }
                  }
                }

              } yield ()
          }
        )

    private def handleLocationMessage(
        user: User,
        latitude: Double,
        longitude: Double,
      ): F[Unit] =
      redis
        .get(user.id.toString + "+companyName")
        .flatMap(companyOpt =>
          companyOpt.fold(Applicative[F].unit) { company =>
            for {
              id <- ID.make[F, LocationId]
              _ <- corporationsRepository.createLocation(
                Location(id = id, name = company, latitude = latitude, longitude = longitude)
              )
              _ <- redis.put(user.id.toString + "+location", id.toString, 60.minutes)
              _ <- telegramClient.sendMessage(
                user.id,
                s"$company dagi lavozimingiz:",
                replyMarkup = ReplyInlineKeyboardMarkup(
                  List(
                    List(InlineKeyboardButton("Direktor", "admin")),
                    List(InlineKeyboardButton("Manager", "manager")),
                  )
                ).some,
              )
            } yield ()
          }
        )

    private def sendUserInfo(
        chatId: Long,
        role: Role,
        person: dto.Person,
        corporateName: NonEmptyString,
        asset: Asset,
      ): F[Unit] =
      s3Client.downloadObject(asset.s3Key.value).compile.to(Array).flatMap { byteArray =>
        telegramClient.sendPhoto(
          chatId = chatId,
          photo = byteArray,
          caption = s"""Ism Familiya: ${person.fullName}
                       |Tug'ilgan kun: ${person.dateOfBirth}
                       |Hujjat raqami: ${person.documentNumber}
                       |Jins: ${person.gender}
                       |Pinfl: ${person.pinflNumber}
                       |
                       |Lavozim: $role""".stripMargin.some,
          replyMarkup = menuButtons(corporateName.value).some,
        )
      }

    private def sendCompanySetting(chatId: Long): F[Unit] =
      (for {
        personId <- OptionT(telegramRepository.findByChatId(chatId))
        user <- OptionT(usersRepository.findById(personId))
        corporate <- OptionT(corporationsRepository.findById(user.corporateId))
        result <- OptionT.liftF {
          corporate.photo match {
            case Some(assetId) =>
              assetsRepository.findAsset(assetId).flatMap {
                case Some(asset) =>
                  s3Client
                    .downloadObject(asset.s3Key.value)
                    .compile
                    .to(Array)
                    .flatMap(byteArray =>
                      telegramClient.sendPhoto(
                        chatId,
                        byteArray,
                        caption =
                          s"Kompaniya nomi: ${corporate.name}\nManzil: ${corporate.locationName}".some,
                        replyMarkup = companyButtons.some,
                      )
                    )
                case None => Applicative[F].unit
              }
            case None =>
              telegramClient.sendMessage(chatId, user.corporateName.value, companyButtons.some)
          }
        }
      } yield result).getOrElseF(Applicative[F].unit)

    private def sendEmployees(
        chatId: Long
      ): F[Unit] =
      telegramClient.sendMessage(
        chatId = chatId,
        text = "test",
        replyMarkup = employeeButtons.some,
      )

    private def sendSettings(
        chatId: Long
      ): F[Unit] =
      telegramClient.sendMessage(
        chatId = chatId,
        text = "test",
        replyMarkup = settingButtons.some,
      )

    private def sendProjects(
        chatId: Long
      ): F[Unit] =
      telegramClient.sendMessage(
        chatId = chatId,
        text = "test",
        replyMarkup = projectButtons.some,
      )

    private def sendTasks(
        chatId: Long
      ): F[Unit] =
      telegramClient.sendMessage(
        chatId = chatId,
        text = "test",
        replyMarkup = taskButtons.some,
      )

    private def sendMonitoring(
        chatId: Long
      ): F[Unit] =
      telegramClient.sendMessage(
        chatId = chatId,
        text = "test",
      )

    private def sendMenu(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId = chatId,
        text = "test",
        replyMarkup = menuButtons(" ").some,
      )

    private def sendContactRequest(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId,
        "Iltimos raqamingizni yuboring.",
        ReplyKeyboardMarkup(
          List(List(KeyboardButton("Raqam yuborish ☎\uFE0F", requestContact = true)))
        ).some,
      )

    private def saveBotUser(chatId: Long, personId: PersonId): F[Unit] =
      telegramRepository
        .findByChatId(chatId)
        .flatMap(personOpt =>
          personOpt.fold(telegramRepository.createBotUser(BotUser(personId, chatId)))(_ =>
            Applicative[F].unit
          )
        )

    private def getFileType(filename: String): String = {
      val extension = filename.substring(filename.lastIndexOf('.') + 1)
      extension.toLowerCase
    }

    private def genFileKey(orgFilename: String): F[String] =
      GenUUID[F].make.map { uuid =>
        uuid.toString + "." + getFileType(orgFilename)
      }
  }
}
