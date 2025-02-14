package tm

import tm.Language._

object ResponseMessages {
  val USER_NOT_FOUND: Map[Language, String] = Map(
    En -> "User not found",
    Ru -> "Пользователь не найден",
    Uz -> "Foydalanuvchi topilmadi",
  )

  val USER_UPDATED: Map[Language, String] = Map(
    En -> "User updated",
    Ru -> "Пользователь обновлен",
    Uz -> "Foydalanuvchi yangilandi",
  )

  val USER_DELETED: Map[Language, String] = Map(
    En -> "User deleted",
    Ru -> "Пользователь удален",
    Uz -> "Foydalanuvchi o'chirildi",
  )

  val PASSWORD_DOES_NOT_MATCH: Map[Language, String] = Map(
    En -> "Sms code does not match",
    Ru -> "Код подтверждения не совпадает",
    Uz -> "SMS kodi mos kelmadi",
  )

  val LIMIT_EXCEEDED: Map[Language, String] = Map(
    En -> "Login Attempt Limit Exceeded:\nYou've made too many unsuccessful login attempts.\nFor security reasons, please try again after 24 hours.",
    Ru -> "Превышен лимит попыток входа:\nВы предприняли слишком много неудачных попыток входа.\nВ целях безопасности повторите попытку через 24 часа.",
    Uz -> "Kirish urinishlari chegarasidan oshib ketdi:\nKirish uchun juda koʻp muvaffaqiyatsiz urinishlar qildingiz.\nXavfsizlik nuqtai nazaridan, 24 soatdan keyin qayta urinib ko'ring.",
  )

  val INVALID_TOKEN: Map[Language, String] = Map(
    En -> "Invalid token or expired",
    Ru -> "Неверный токен или токен устарел",
    Uz -> "Yaroqsiz yoki eskirgan token",
  )

  val BEARER_TOKEN_NOT_FOUND: Map[Language, String] = Map(
    En -> "Bearer token not found",
    Ru -> "Токен не найден",
    Uz -> "Bearer token topilmadi",
  )

  val OTP_SENT: Map[Language, String] = Map(
    En -> "OTP sent",
    Ru -> "OTP отправлен",
    Uz -> "OTP yuborildi",
  )

  val TEACHER_NOT_FOUND: Map[Language, String] = Map(
    En -> "Teacher not found",
    Ru -> "Учитель не найден",
    Uz -> "Oʻtib topilmadi",
  )

  val SUBJECT_NOT_FOUND: Map[Language, String] = Map(
    En -> "Subject not found",
    Ru -> "Предмет не найден",
    Uz -> "Mavzu topilmadi",
  )

  val SUBJECT_DELETED: Map[Language, String] = Map(
    En -> "Subject deleted",
    Ru -> "Предмет удален",
    Uz -> "Mavzu o'chirildi",
  )

  val SUBJECT_UPDATED: Map[Language, String] = Map(
    En -> "Subject updated",
    Ru -> "Предмет обновлен",
    Uz -> "Mavzu yangilandi",
  )

  val ROOM_NOT_FOUND: Map[Language, String] = Map(
    En -> "Room not found",
    Ru -> "Комната не найдена",
    Uz -> "Xona topilmadi",
  )

  val ROOM_UPDATED: Map[Language, String] = Map(
    En -> "Room updated",
    Ru -> "Комната обновлена",
    Uz -> "Xona yangilandi",
  )

  val ROOM_DELETED: Map[Language, String] = Map(
    En -> "Room deleted",
    Ru -> "Комната удалена",
    Uz -> "Xona o'chirildi",
  )

  val GROUP_NOT_FOUND: Map[Language, String] = Map(
    En -> "Group not found",
    Ru -> "Группа не найдена",
    Uz -> "Guruh topilmadi",
  )

  val GROUP_UPDATED: Map[Language, String] = Map(
    En -> "Group updated",
    Ru -> "Группа обновлена",
    Uz -> "Guruh yangilandi",
  )

  val GROUP_DELETED: Map[Language, String] = Map(
    En -> "Group deleted",
    Ru -> "Группа удалена",
    Uz -> "Guruh o'chirildi",
  )

  val STUDENT_NOT_FOUND: Map[Language, String] = Map(
    En -> "Student not found",
    Ru -> "Студент не найден",
    Uz -> "O'quvchi topilmadi",
  )

  val STUDENT_UPDATED: Map[Language, String] = Map(
    En -> "Student updated",
    Ru -> "Студент обновлен",
    Uz -> "O'quvchi yangilandi",
  )
}
