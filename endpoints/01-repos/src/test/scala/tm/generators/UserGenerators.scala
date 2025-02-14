package tm.generators

import org.scalacheck.Gen
import tsec.passwordhashers.jca.SCrypt

import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User

trait UserGenerators { this: Generators =>
  def createUserGen(personId: PersonId): Gen[AccessCredentials[User]] =
    for {
      role <- roleGen
      phone <- phoneGen
    } yield AccessCredentials[User](
      data = User(
        id = personId,
        role = role,
        phone = phone,
      ),
      password = SCrypt.hashpwUnsafe(phone.value),
    )
}
