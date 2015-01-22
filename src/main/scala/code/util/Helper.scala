package code.util

import net.liftweb.common._
import net.liftweb.util.{Mailer, Props}
import net.liftweb.util.Helpers._

object Helper{

  /**
   * A css selector that will (unless you have a template containing an element
   * name i_am_an_id_that_should_never_exist) have no effect. Useful when you have
   * a method that needs to return a CssSel but in some code paths don't want to do anything.
   */
  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""

  def generatePermalink(name: String): String = {
    name.trim.toLowerCase.replace("-","").replaceAll(" +", " ").replaceAll(" ", "-")
  }

  /**
   * Useful for integrating failure message in for comprehensions.
   *
   * Normally a for comprehension might look like:
   *
   * for {
   *   account <- Account.find(...) ?~ "Account not found"
   *   if(account.isPublic)
   * } yield account
   *
   * The issue here is that we can't easily add an error message to describe why this might fail (i.e
   * if the account not public)
   *
   * Using this function, we can instead write
   *
   * for {
   *   account <- Account.find(...) ?~ "Account not found"
   *   accountIsPublic <- booleanToBox(account.isPublic, "Account is not public")
   * } yield account
   *
   * It's not ideal, but it works.
   *
   * @param statement A boolean condition
   * @param msg The message to give the Failure option if "statement" is false
   * @return A box that is Full if the condition was met, and a Failure(msg) if not
   */
  def booleanToBox(statement: => Boolean, msg: String): Box[Unit] = {
    if(statement)
      Full()
    else
      Failure(msg)
  }

  def booleanToBox(statement: => Boolean): Box[Unit] = {
    if(statement)
      Full()
    else
      Empty
  }

  val deprecatedJsonGenerationMessage = "json generation handled elsewhere as it changes from api version to api version"
}