package code.tesobe

import java.util.Date

import code.model.dataAccess.{Account, OBPEnvelope}
import net.liftweb.common.{Full, Loggable}
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.{JField, JObject, JArray}
import net.liftweb.mongodb.Limit
import net.liftweb.util.Props
import net.liftweb.util.Helpers._
import net.liftweb.json.JsonDSL._

object ImporterAPI extends RestHelper with Loggable {

  case class EnvelopesToInsert(l: List[OBPEnvelope])
  case class InsertedEnvelopes(l: List[OBPEnvelope])

  def errorJsonResponse(message : String = "error", httpCode : Int = 400) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), Nil, Nil, httpCode)


  /**
   * A JSON representation of the transaction to be returned when successfully added via an API call
   */
  def whenAddedJson(env : OBPEnvelope) : JObject = {
    JObject(
      List(
        JField("obp_transaction", env.obp_transaction.get.whenAddedJson(env.id.toString))
      )
    )
  }

  serve {
    
    case "api" :: "transactions" :: Nil JsonPost json => {

      def savetransactions ={
        val rawEnvelopes = json._1.children
        val envelopes : List[OBPEnvelope]= rawEnvelopes.flatMap(e => {
          OBPEnvelope.envelopesFromJValue(e)
        })

        def updateAccountBalance(accountNumber: String, bankId: String, account: Account) = {
          val newest =
            OBPEnvelope.findAll(
              ("obp_transaction.this_account.number" -> accountNumber) ~
                ("obp_transaction.this_account.bank.national_identifier" -> bankId),
              ("obp_transaction.details.completed" -> -1),
              Limit(1)
            ).headOption

          if(newest.isDefined) {
            logger.debug(s"Updating current balance for account $accountNumber at bank $bankId")
            account.accountBalance(newest.get.obp_transaction.get.details.get.new_balance.get.amount.get).save
          }
          else
            logger.warn("Could not update the balance for the account $accountNumber at bank $bankId")
        }
        def updateBankAccount(insertedEnvelopes: List[OBPEnvelope]) = {
          if(insertedEnvelopes.nonEmpty) {
            //we assume here that all the Envelopes concerns only one account
            val envelope = insertedEnvelopes(0)
            val thisAccount = envelope.obp_transaction.get.this_account.get
            val accountNumber = thisAccount.number.get
            val bankId = thisAccount.bank.get.national_identifier.get
            envelope.theAccount match {
              case Full(account) =>  {
                account.lastUpdate(new Date).save
                updateAccountBalance(accountNumber, bankId, account)
              }
              case _ => logger.info("account $accountNumber at bank $bankId not found")
            }
          }
        }

        val ipAddress = json._2.remoteAddr
        logger.info("Received " + rawEnvelopes.size +
          " json transactions to insert from ip address " + ipAddress)
        logger.info("Received " + envelopes.size +
          " valid transactions to insert from ip address " + ipAddress)

        /**
         * Using an actor to do insertions avoids concurrency issues with
         * duplicate transactions by processing transaction batches one
         * at a time. We'll have to monitor this to see if non-concurrent I/O
         * is too inefficient. If it is, we could break it up into one actor
         * per "Account".
         */

        val l = EnvelopesToInsert(envelopes)
        // TODO: this duration limit should be fixed
        val createdEnvelopes = EnvelopeInserter !? (3 minutes, l)

        createdEnvelopes match {
          case Full(env: InsertedEnvelopes) =>{
            val insertedEnvelopes = env.l
            logger.info("inserted " + insertedEnvelopes.size + " transactions")
            updateBankAccount(insertedEnvelopes)
            val jsonList = insertedEnvelopes.map(whenAddedJson)
            JsonResponse(JArray(jsonList))
          }
          case _ => {
            logger.warn("no envelopes inserted")
            InternalServerErrorResponse()
          }
        }
      }

      S.param("secret") match {
        case Full(s) => {
          Props.get("importer_secret") match {
            case Full(localS) =>
              if(localS == s)
                savetransactions
              else
                errorJsonResponse("wrong secret", 401)
            case _ => errorJsonResponse("importer_secret not set")
          }
        }
        case _ => errorJsonResponse("secret missing")
      }

    }
  }
}