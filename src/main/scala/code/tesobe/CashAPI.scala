package code.tesobe

import java.util.Date
import code.bankconnectors.Connector
import code.model.dataAccess._
import net.liftweb.common.{Full, Box, Loggable}
import net.liftweb.http.{JsonResponse, S}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{JsonAST, Extraction}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Props
import net.liftweb.util.Helpers._
import net.liftweb.json.pretty
import net.liftweb.json.JsonDSL._

import scala.math.BigDecimal.RoundingMode

case class CashTransaction(
  otherParty : String,
  date : Date,
  amount : Double,
  kind : String,
  label : String,
  otherInformation : String
  )

case class ErrorMessage(error: String)

case class SuccessMessage(success: String)

object CashAccountAPI extends RestHelper with Loggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  implicit def successToJson(msg: SuccessMessage): JValue = Extraction.decompose(msg)

  def isValidKey : Boolean = {
    val sentKey : Box[String] =
      for{
        req <- S.request
        sentKey <- req.header("cashApplicationKey")
      } yield sentKey
    val localKey : Box[String] = Props.get("cashApplicationKey")
    localKey == sentKey
  }

  lazy val invalidCashTransactionJsonFormatError = {
    val error = "Post data must be in the format: \n" +
      pretty(
        JsonAST.render(
          Extraction.decompose(
            CashTransaction(
              otherParty = "other party",
              date = new Date,
              amount = 1231.12,
              kind = "in / out",
              label = "what is this transaction for",
              otherInformation = "more info"
            ))))
    JsonResponse(ErrorMessage(error), Nil, Nil, 400)
  }

  serve("obp" / "v1.0" prefix {

    case "cash-accounts" :: uuid :: "transactions" :: Nil JsonPost json -> _ => {

      val connector = Connector.connector.vend

      if(isValidKey) {
        connector.getAccountByUUID(uuid) match {
          case Full(account) =>
            tryo { json.extract[CashTransaction] } match {
              case Full(cashTransaction) =>
                connector.addCashTransactionAndUpdateBalance(account, cashTransaction)
                JsonResponse(SuccessMessage("transaction successfully added"), Nil, Nil, 200)
              case _ =>
                invalidCashTransactionJsonFormatError
            }
          case _ =>
            JsonResponse(ErrorMessage("Account " + uuid + " not found" ), Nil, Nil, 400)
        }

      } else {
        JsonResponse(ErrorMessage("No key found or wrong key"), Nil, Nil, 401)
      }

    }

  })
}
