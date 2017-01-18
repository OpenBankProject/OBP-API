package code.loginattempts

import net.liftweb.common.{Loggable, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

object LoginAttempt extends Loggable {

  val maxBadLoginAttempts = Props.get("max.bad.login.attempts") openOr "5"
  
  def incrementBadLoginAttempts(username: String): Unit = {

    logger.info(s"Hello from incrementBadLoginAttempts with $username")

    // Find badLoginAttempt record if one exists for a user
    MappedBadLoginAttempt.find(By(MappedBadLoginAttempt.mUsername, username)) match {
      // If it exits update the date and increment
      case Full(loginAttempt) =>

        logger.info(s"incrementBadLoginAttempts found ${loginAttempt.mBadAttemptsSinceLastSuccessOrReset} loginAttempt(s) with id ${loginAttempt.id}")

        loginAttempt
          .mLastFailureDate(now)
          .mBadAttemptsSinceLastSuccessOrReset(loginAttempt.mBadAttemptsSinceLastSuccessOrReset + 1) // Increment
          .save
      case _ =>
        // If none exists, add one
        val newLoginAttempt = MappedBadLoginAttempt.create
          .mUsername(username)
          .mLastFailureDate(now)
          .mBadAttemptsSinceLastSuccessOrReset(1) // Start with 1
          .save()

        logger.info(s"incrementBadLoginAttempts created loginAttempt")


    }
  }

  /**
    * check the bad login attempts,if it exceed the "max.bad.login.attempts"(in default.props), it return false.
    */
  def userIsLocked(username: String): Boolean = {

    val result : Boolean = MappedBadLoginAttempt.find(By(MappedBadLoginAttempt.mUsername, username)) match {
      case Empty => false // No records so not locked
      case Full(loginAttempt)  => loginAttempt.badAttemptsSinceLastSuccessOrReset > maxBadLoginAttempts.toInt match {
        case true => true
        case false => false
      }
      case _ => false
    }

    logger.info(s"userIsLocked result for $username is $result")
    result

  }

  def resetBadLoginAttempts(username: String): Unit = {

    MappedBadLoginAttempt.find(By(MappedBadLoginAttempt.mUsername, username)) match {
      case Full(loginAttempt) =>
        loginAttempt.mLastFailureDate(now).mBadAttemptsSinceLastSuccessOrReset(0).save
      case _ =>
        // don't need to create here
        Empty // MappedBadLoginAttempt.create.mUsername(username).mBadAttemptsSinceLastSuccessOrReset(0).save()
    }
  }

} // End of Trait