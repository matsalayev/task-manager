package tm.generators

import org.scalacheck.Gen
import tm.Language
import tm.domain._
import tm.domain.enums.Gender
import tm.domain.enums.Role
import tm.domain.enums.WorkState
import tm.domain.enums.WorkStatus
import tm.domain.enums.WorkType
import tm.test.generators.Generators

trait TypeGen { this: Generators =>
  val personIdGen: Gen[PersonId] = idGen(PersonId.apply)
  val assetIdGen: Gen[AssetId] = idGen(AssetId.apply)
  val roleGen: Gen[Role] = Gen.oneOf(Role.values)
  val genderGen: Gen[Gender] = Gen.oneOf(Gender.values)
  val languageGen: Gen[Language] = Gen.oneOf(Language.values)
  val workStateGen: Gen[WorkState] = Gen.oneOf(WorkState.values)
  val workTypeGen: Gen[WorkType] = Gen.oneOf(WorkType.values)
  val workStatusGen: Gen[WorkStatus] = Gen.oneOf(WorkStatus.values)
}
