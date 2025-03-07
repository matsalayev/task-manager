package tm.endpoint

import org.http4s.AuthedRequest
import org.http4s.Request

import tm.domain.auth.AuthedUser
import tm.domain.enums.Role

package object routes {
  object asAdmin {
    def unapply[F[_]](ar: AuthedRequest[F, AuthedUser]): Option[(Request[F], AuthedUser)] =
      Option.when(ar.context.role == Role.Director)(
        ar.req -> ar.context
      )
  }
}
