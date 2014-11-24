package code.util

import net.liftweb.common._
import net.liftweb.util.{Mailer, Props}

object Helper{

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

  /**
   * Converts a number representing the smallest unit of a currency into a big decimal formatted according to the rules of
   * that currency. E.g. JPY: 1000 units (yen) => 1000, EUR: 1000 units (cents) => 10.00
   */
  def smallestCurrencyUnitToBigDecimal(units : Long, currencyCode : String) = {
    //this data was sourced from Wikipedia, so it might not all be correct,
    //and some banking systems may still retain different units (e.g. CZK?)
    val decimalPlaces = currencyCode match {
      //TODO: handle MRO and MGA, which are non-decimal
      case "CZK" | "JPY" | "KRW" => 0
      case "KWD" | "OMR" => 3
      case _ => 2
    }
    BigDecimal(units, decimalPlaces)
  }
}