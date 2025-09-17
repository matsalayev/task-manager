package tm

import cats.effect.Async

import tm.auth.impl.Auth
import tm.domain.auth.AuthedUser
import tm.support.redis.RedisClient

case class JobsEnvironment[F[_]](
    auth: Auth[F, AuthedUser],
    redis: RedisClient[F],
    // tasks: TasksRepository[F],
  )

object JobsEnvironment {
  def make[F[_]: Async](
      repositories: Repositories[F],
      redis: RedisClient[F],
      auth: Auth[F, AuthedUser],
    ): JobsEnvironment[F] =
    JobsEnvironment[F](
      auth = auth,
      redis = redis,
      // tasks = repositories.tasksRepository,
    )
}
