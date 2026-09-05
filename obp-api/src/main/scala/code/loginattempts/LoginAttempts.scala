package code.loginattempts

import java.util.Date

import code.api.util.APIUtil
import code.bankconnectors.DoobieBadLoginAttemptQueries
import code.userlocks.UserLocksProvider
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers._

trait BadLoginAttempt {
  def username: String
  def provider: String
  def badAttemptsSinceLastSuccessOrReset : Int
  def lastFailureDate : Date
}

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
        val rowsUpdated = DoobieBadLoginAttemptQueries.incrementBadLoginAttempts(provider, username)
        if (rowsUpdated == 0) {
          tryo {
            DoobieBadLoginAttemptQueries.create(provider, username, 1)
          }
          logger.debug(s"incrementBadLoginAttempts created loginAttempt")
        } else {
          logger.debug(s"incrementBadLoginAttempts atomically incremented for $username (rows=$rowsUpdated)")
        }
    }
  }

  def getOrCreateBadLoginStatus(provider: String, username: String): Box[BadLoginAttempt] = {
    DoobieBadLoginAttemptQueries.find(provider, username) match {
      case Some(row) => Full(row)
      case None =>
        // Two concurrent first-time callers can both miss the find above and both try to
        // create; the loser hits UniqueIndex(Provider, mUsername).
        tryo {
          DoobieBadLoginAttemptQueries.create(provider, username, 0)
        } match {
          case full @ Full(_) => full
          case Failure(_, _, _) =>
            // UniqueIndex violation from concurrent insert — re-fetch the committed row
            DoobieBadLoginAttemptQueries.find(provider, username) match {
              case Some(row) => Full(row)
              case None      => Empty
            }
          case other => other
        }
    }
  }

  /**
    * check the bad login attempts, if it exceed the "max.bad.login.attempts"(in default.props), it return false.
    */
  def userIsLocked(provider: String, username: String): Boolean = {

    val result: Boolean = DoobieBadLoginAttemptQueries.find(provider, username) match {
      case Some(loginAttempt) => loginAttempt.badAttemptsSinceLastSuccessOrReset > maxBadLoginAttempts.toInt match {
        case true => true
        case false => UserLocksProvider.isLocked(provider, username) // Check the table UserLocks
      }
      case _ => UserLocksProvider.isLocked(provider, username) // Check the table UserLocks
    }

    logger.debug(s"userIsLocked result for $username is $result")
    result

  }

  def resetBadLoginAttempts(provider: String, username: String): Unit = {
    DoobieBadLoginAttemptQueries.resetBadLoginAttempts(provider, username)
    // don't need to create here - matches the Mapper version, which only ever updated an
    // existing row and left a missing one alone.
  }

} // End of Trait
