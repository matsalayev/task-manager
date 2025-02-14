package tm

case class JobsEnvironment[F[_]](
    repos: JobsEnvironment.Repositories[F],
    adminPhone: Phone,
  )

object JobsEnvironment {
  case class Repositories[F[_]](
    )
}
