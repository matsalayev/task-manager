package tm.generators

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import org.scalacheck.Gen

import tm.Email
import tm.Language
import tm.domain._
import tm.domain.enums.Gender
import tm.domain.enums.Role
import tm.test.generators.Generators

trait TypeGen { this: Generators =>
  val personIdGen: Gen[PersonId] = idGen(PersonId.apply)
  val assetIdGen: Gen[AssetId] = idGen(AssetId.apply)
  val roleGen: Gen[Role] = Gen.oneOf(Role.values)
  val genderGen: Gen[Gender] = Gen.oneOf(Gender.values)
  val languageGen: Gen[Language] = Gen.oneOf(Language.values)
  val emailGen: Gen[Email] = for {
    username <- Gen.alphaLowerStr.suchThat(_.length >= 3)
    domain <- Gen.alphaLowerStr.suchThat(_.length >= 3)
    tld <- Gen.oneOf("com", "org", "net", "uz", "ru")
    email = s"$username@$domain.$tld"
  } yield email.asInstanceOf[Email]
}
