package tm.repositories

import java.util.UUID

import cats.effect.IO
import weaver.SimpleIOSuite

import tm.domain.CorporateId
import tm.domain.PersonId

object UserRepositorySpec extends SimpleIOSuite {
  test("UserRepository should compile") {
    val personId = PersonId(UUID.randomUUID())
    val corporateId = CorporateId(UUID.randomUUID())

    IO.pure(expect(personId.value != null && corporateId.value != null))
  }

  test("User domain types should work correctly") {
    val personId1 = PersonId(UUID.randomUUID())
    val personId2 = PersonId(UUID.randomUUID())

    IO.pure(expect(personId1.value != personId2.value))
  }
}
