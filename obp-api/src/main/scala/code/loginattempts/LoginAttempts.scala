package code.loginattempts

import code.api.util.APIUtil
import code.userlocks.UserLocksProvider
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._

object LoginAttempt extends MdcLoggable {

  def maxBadLoginAttempts = APIUtil.getPropsValue("max.bad.login.attempts") openOr "5"
  
  def incrementBadLoginAttempts(provider: String, username: String): Unit = {
    username.isEmpty() match {
      case true => // Not a valid case. GitLab issue 389
        logger.warn(s"Username is empty: incrementBadLoginAttempts(username=$username, provider=$provider")
      case false =>
        logger.debug(s"Hello from incrementBadLoginAttempts with $username")

        // Atomically increment the counter; if no row exists yet, create one.
        // The create path is itself a check-then-insert: two concurrent first-time bad logins both
        // see rowsUpdated==0, so wrap in tryo to absorb the UniqueIndex violation from the loser.
        val rowsUpdated = code.bankconnectors.DoobieBadLoginAttemptQueries.incrementBadLoginAttempts(provider, username)
        if (rowsUpdated == 0) {
          tryo {
            MappedBadLoginAttempt.create
              .mUsername(username)
              .Provider(provider)
              .mLastFailureDate(now)
              .mBadAttemptsSinceLastSuccessOrReset(1)
              .save
          }
          logger.debug(s"incrementBadLoginAttempts created loginAttempt")
        } else {
          logger.debug(s"incrementBadLoginAttempts atomically incremented for $username (rows=$rowsUpdated)")
        }
    }
  }
  
  def getOrCreateBadLoginStatus(provider: String, username: String): Box[BadLoginAttempt] = {
    MappedBadLoginAttempt.find(
      By(MappedBadLoginAttempt.Provider, provider),
      By(MappedBadLoginAttempt.mUsername, username)
    ) match {
      case full @ Full(_) => full
      case _ =>
        // .or(Full(saveMe())) evaluates saveMe eagerly — two concurrent first-time callers
        // both get Empty and both call saveMe; the loser hits UniqueIndex(Provider, mUsername).
        tryo {
          MappedBadLoginAttempt.create
            .mUsername(username)
            .Provider(provider)
            .mLastFailureDate(now)
            .mBadAttemptsSinceLastSuccessOrReset(0)
            .saveMe()
        } match {
          case full @ Full(_) => full
          case Failure(_, _, _) =>
            // UniqueIndex violation from concurrent insert — re-fetch the committed row
            MappedBadLoginAttempt.find(
              By(MappedBadLoginAttempt.Provider, provider),
              By(MappedBadLoginAttempt.mUsername, username)
            )
          case other => other
        }
    }
  }

  /**
    * check the bad login attempts, if it exceed the "max.bad.login.attempts"(in default.props), it return false.
    */
  def userIsLocked(provider: String, username: String): Boolean = {

    val result : Boolean = MappedBadLoginAttempt.find( // Check the table MappedBadLoginAttempt
      By(MappedBadLoginAttempt.Provider, provider),
      By(MappedBadLoginAttempt.mUsername, username)
    ) match {
      case Full(loginAttempt)  => loginAttempt.badAttemptsSinceLastSuccessOrReset > maxBadLoginAttempts.toInt match {
        case true => true
        case false => UserLocksProvider.isLocked(provider, username) // Check the table UserLocks
      }
      case _ => UserLocksProvider.isLocked(provider, username) // Check the table UserLocks
    }

    logger.debug(s"userIsLocked result for $username is $result")
    result

  }

  def resetBadLoginAttempts(provider: String, username: String): Unit = {

    MappedBadLoginAttempt.find(
      By(MappedBadLoginAttempt.Provider, provider),
      By(MappedBadLoginAttempt.mUsername, username)
    ) match {
      case Full(loginAttempt) =>
        loginAttempt.mLastFailureDate(now).mBadAttemptsSinceLastSuccessOrReset(0).save
      case _ =>
        // don't need to create here
        Empty // MappedBadLoginAttempt.create.mUsername(username).mBadAttemptsSinceLastSuccessOrReset(0).save()
    }
  }

} // End of Trait