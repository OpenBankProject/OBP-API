package code.opentok

import code.api.util.APIUtil
import com.opentok._
import com.opentok.exception.OpenTokException

/**
  * Created by markom on 5/22/16.
  */
object OpenTokUtil {
  private var session: Session = null

  def createOpenTok: OpenTok = {
    // Set the following constants with the API key and API secret
    // that you receive when you sign up to use the OpenTok API:
    val apiKey: Int = APIUtil.getPropsValue("meeting.tokbox_api_key", "0000").toInt
    val apiSecret: String = APIUtil.getPropsValue("meeting.tokbox_api_secret", "YOUR API SECRET")
    val opentok: OpenTok = new OpenTok(apiKey, apiSecret)
    return opentok
  }

  @throws[OpenTokException]
  def getSession: Session = {
    if (session == null) {
      // A session that uses the OpenTok Media Router:
      session = createOpenTok.createSession(new SessionProperties.Builder().mediaMode(MediaMode.ROUTED).build)
    }
    return session
  }

  @throws[OpenTokException]
  def generateTokenForModerator(expireTimeInMinutes: Int): String = {
    // Generate a token. Use the Role MODERATOR. Expire time is defined by parameter expireTimeInMinutes.
    val token: String = session.generateToken(new TokenOptions.Builder().role(Role.MODERATOR).expireTime((System.currentTimeMillis / 1000L) + (expireTimeInMinutes * 60)).data // in expireTimeInMinutes
      ("name=Simon").build)
    return token
  }

  @throws[OpenTokException]
  def generateTokenForPublisher(expireTimeInMinutes: Int): String = {
    // Generate a token. Use the Role PUBLISHER. Expire time is defined by parameter expireTimeInMinutes.
    val token: String = session.generateToken(new TokenOptions.Builder().role(Role.PUBLISHER).expireTime((System.currentTimeMillis / 1000L) + (expireTimeInMinutes * 60)).data // in expireTimeInMinutes
      ("name=Simon").build)
    return token
  }
}

class OpenTokUtil() // Empty constructor
  extends Exception {
}