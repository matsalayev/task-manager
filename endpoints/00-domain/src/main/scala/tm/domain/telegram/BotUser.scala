package tm.domain.telegram

import tm.domain.PersonId

case class BotUser(
    parentId: PersonId,
    chatId: Long,
  )
