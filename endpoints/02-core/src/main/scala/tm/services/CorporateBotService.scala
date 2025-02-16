package tm.services

import java.time.LocalDate
import java.util.UUID

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import cats.Applicative
import cats.Monad
import cats.effect.kernel.Sync
import cats.implicits.catsSyntaxOptionId
import cats.implicits.catsSyntaxTuple2Semigroupal
import cats.implicits.catsSyntaxTuple3Semigroupal
import cats.implicits.catsSyntaxTuple4Semigroupal
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
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

        case regexFullName(firstName, lastName) =>
          redis.get(user.id.toString + "+phone").flatMap {
            case Some(_) =>
              for {
                today <- Calendar[F].currentDate
                _ <- telegramClient.sendMessage(
                  user.id,
                  s"Ism: $firstName\nFamiliya: $lastName\n\nIltimos tug'ilgan kuningizni quyidagi formatda yozib yuboring:\n\n$today",
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
              telegramClient.sendMessage(
                user.id,
                s"Ism: ${nameParts.head}\nFamiliya: ${nameParts.last}\nTug'ilgan kun: $date\n\nIltimos hujjat raqamingizni yozib yuboring:\n\nAC1234567",
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
              telegramClient.sendMessage(
                user.id,
                s"Ism: ${nameParts.head}\nFamiliya: ${nameParts.last}\nTug'ilgan kun: $date\nHujjat raqami: $document\n\nIltimos pinfl raqamingizni yozib yuboring:\n\n56001234567890",
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
              for {
                _ <- telegramClient.sendMessage(
                  user.id,
                  s"Ism: ${nameParts.head}\nFamiliya: ${nameParts.last}\nTug'ilgan kun: $date\nHujjat raqami: $document\nPinfl: $pinfl\n\nIltimos suratingizni yuboring.",
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
        case regexCompanyName(companyName) => logger.info(s"$companyName")

        case regexCompanyName(companyName) =>
          (
            redis.get(user.id.toString + "+phone"),
            redis.get(user.id.toString + "+photo"),
            redis.put(user.id.toString + "+companyName", companyName, 60.minute),
          ).tupled.flatMap {
            case (Some(_), Some(_), _) =>
              telegramClient.sendMessage(
                user.id,
                s"Kamponiya nomi: $companyName \nIltimos kamponiyangiz manzilini yuboring.",
              )
            case _ => Applicative[F].unit
          }

        case _ => logger.info("undefined behaviour for corporate bot")
      }
    }

    private def handleContactMessage(user: User, contact: Contact): F[Unit] =
      contact match {
        case Contact(phoneNumberStr, Some(userTelegramId)) =>
          val phoneNumber: Phone =
            if (phoneNumberStr.startsWith("+")) phoneNumberStr else s"+$phoneNumberStr"
          if (user.id == userTelegramId)
            usersRepository.findByPhone(phoneNumber).flatMap {
              case Some(corporateUser) => logger.info(s"$corporateUser")
              // saveBotUser(user.id, corporateUser.id).flatMap(_ => sendEmployeeInfo(user.id))
              case None =>
                redis.put(user.id.toString + "+phone", phoneNumberStr, 60.minute).flatMap { _ =>
                  telegramClient.sendMessage(
                    user.id,
                    s"Uzr sizni foydalanuvchilar orasidan topa olmadik!\nAgar bot foydalanuvchisi sifatida ro'yxatdan o'tmoqchi bo'lsangiz ism va familiyangizni yozib yuboring:\n\nIsm Familiya",
                    ReplyKeyboardRemove().some,
                  )
                }
            }
          else Applicative[F].unit
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
                      _ <- corporationsRepository.create(
                        Corporate(
                          id = id,
                          createdAt = now,
                          name = companyName,
                          locationId = LocationId(UUID.fromString(location)),
                          photo = None,
                        )
                      )
                      role = Role.withName(data.value)
                      _ <- usersRepository.createUser(
                        corporate.User(
                          id = personId,
                          role = role,
                          phone = phone,
                          asset_id = AssetId(UUID.fromString(photo)).some,
                          corporate_id = id,
                        )
                      )
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
                        _ <- redis.put(user.id.toString + "+photo", id.value, 60.minutes)
                        _ <- telegramClient.sendMessage(
                          user.id,
                          "Yana bir nechta bosqich qoldi.\nIltimos kamponiyangiz nomini '' ichida yozib yuboring:\n\n'Kamponiya'",
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
              _ <- redis.put(user.id.toString + "+location", id.value, 60.minutes)
              _ <- telegramClient.sendMessage(
                user.id,
                s"$company dagi lavozimingiz:",
                reply_markup = ReplyInlineKeyboardMarkup(
                  List(
                    List(InlineKeyboardButton("Direktor", Role.Admin.toString)),
                    List(InlineKeyboardButton("Manager", Role.Manager.toString)),
                  )
                ).some,
              )
            } yield ()
          }
        )

    private def sendContactRequest(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId,
        "Iltimos raqamingizni yuboring.",
        ReplyKeyboardMarkup(
          List(List(KeyboardButton("Raqam yuborish ☎\uFE0F", requestContact = true)))
        ).some,
      )

    private def sendEmployeeInfo(chatId: Long): F[Unit] =
      telegramRepository.findByChatId(chatId).flatMap {
        case Some(personId) =>
          employeeRepository.findById(personId).flatMap {
            case Some(employee) =>
              for {
                _ <- telegramClient.sendMessage(
                  chatId,
                  s"Assalomu alaykum ${employee.fullName}\nKorporatsiya: ${employee.corporateName}\nLavozim: ${employee.specialtyName}",
                  ReplyKeyboardRemove().some,
                )
              } yield ()
            case _ =>
              telegramClient.sendMessage(
                chatId,
                s"Uzr sizni xodimlar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }
        case _ =>
          telegramClient.sendMessage(
            chatId,
            s"Uzr sizni foydalanuvchilar orasidan topa olmadik!",
            ReplyKeyboardRemove().some,
          )
      }

    private def sendCorporateInfo(chatId: Long): F[Unit] =
      telegramRepository.findByChatId(chatId).flatMap {
        case Some(personId) =>
          employeeRepository.findById(personId).flatMap {
            case Some(employee) =>
              corporationsRepository.findById(employee.corporateId).flatMap {
                case Some(corporate) =>
                  telegramClient.sendMessage(
                    chatId,
                    s"Korporatsiya: ${corporate.name}\nJoylashuv: ${corporate.locationId}",
                    ReplyKeyboardRemove().some,
                  )
                case _ => Applicative[F].unit
              }
            case _ =>
              telegramClient.sendMessage(
                chatId,
                s"Uzr sizni xodimlar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }
        case _ =>
          telegramClient.sendMessage(
            chatId,
            s"Uzr sizni foydalanuvchilar orasidan topa olmadik!",
            ReplyKeyboardRemove().some,
          )
      }

    private def sendProjects(chatId: Long): F[Unit] =
      telegramRepository.findByChatId(chatId).flatMap {
        case Some(personId) =>
          employeeRepository.findById(personId).flatMap {
            case Some(employee) =>
              projectsRepository.getAll(employee.corporateId).flatMap { projects =>
                Applicative[F].unit
              //                case Some(corporate) =>
              //                  telegramClient.sendMessage(
              //                    chatId,
              //                    s"Korporatsiya: ${corporate.name}\nJoylashuv: ${corporate.locationId}",
              //                    ReplyKeyboardRemove().some,
              //                  )
              //                case _ => Applicative[F].unit
              }
            case _ =>
              telegramClient.sendMessage(
                chatId,
                s"Uzr sizni xodimlar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }
        case _ =>
          telegramClient.sendMessage(
            chatId,
            s"Uzr sizni foydalanuvchilar orasidan topa olmadik!",
            ReplyKeyboardRemove().some,
          )
      }

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
