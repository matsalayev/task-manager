package tm.auth

import tm.domain.JwtAccessTokenKey
import tm.domain.TokenExpiration

case class AuthConfig(user: AuthConfig.UserAuthConfig)

object AuthConfig {
  case class UserAuthConfig(
      tokenKey: JwtAccessTokenKey,
      accessTokenExpiration: TokenExpiration,
      refreshTokenExpiration: TokenExpiration,
    )
}
