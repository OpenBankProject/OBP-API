package code.token

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object TokensOpenIDConnect extends SimpleInjector {
  val tokens = new Inject(buildOne _) {}
  def buildOne: OpenIDConnectTokensProvider = MappedOpenIDConnectTokensProvider
}

trait OpenIDConnectTokensProvider {
  def createToken(tokenType: String,
                  accessToken: String,
                  idToken: String,
                  refreshToken: String,
                  scope: String,
                  expiresIn: Long): Box[OpenIDConnectToken]
}

trait OpenIDConnectTokenTrait {
  def accessToken: String
  def idToken: String
  def refreshToken: String
  def scope: String
  def tokenType: String
  def expiresIn: Long
}
