//package digitalschool
//
//import digitalschool.database.DBSuite
//import digitalschool.generators.Generators
//import digitalschool.generators.PeopleGenerators
//import digitalschool.generators.UserGenerators
//import digitalschool.repositories.PeopleRepository
//import digitalschool.repositories.UsersRepository
//
//object UserRepositorySpec
//    extends DBSuite
//       with Generators
//       with UserGenerators
//       with PeopleGenerators {
//  override def schemaName: String = "public"
//
//  test("create user") { implicit postgres =>
//    val createPeople = createPeopleGen
//    val userRepo = UsersRepository.make[F]
//    val peopleRepo = PeopleRepository.make[F]
//
//    for {
//      people <- peopleRepo.create(createPeople)
//      createUser = createUserGen(people.id).gen
//      _ <- userRepo.create(createUser)
//      user <- userRepo.find(createUser.data.phone)
//    } yield assert(user.exists(_.data.id == createUser.data.id))
//  }
//}
