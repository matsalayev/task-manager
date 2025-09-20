package tm.generators

import java.util.UUID

import tm.domain.PersonId

object UserGenerators {
  // Simple user structure for testing
  case class SimpleUser(
      id: PersonId,
      name: String,
      email: String,
    )

  def userGen: SimpleUser = SimpleUser(
    id = PersonId(UUID.randomUUID()),
    name = "Test User",
    email = "test@example.com",
  )
}
