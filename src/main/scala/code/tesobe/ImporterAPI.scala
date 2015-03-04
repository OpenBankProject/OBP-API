 package code.tesobe

import java.util.Date

import code.bankconnectors.Connector
import code.model.Transaction
import code.model.dataAccess.{Account, OBPEnvelope}
import net.liftweb.common.{Full, Loggable}
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{DefaultFormats, Extraction}
import net.liftweb.json.JsonAST.{JString, JField, JObject, JArray}
import net.liftweb.mongodb.Limit
import net.liftweb.util.Props
import net.liftweb.util.Helpers._
import net.liftweb.json.JsonDSL._

/**
 * This is legacy code and does not handle edge cases very well and assumes certain things, e.g.
 * that bank national identifier is unique (when in reality it should only be unique for a given
 * country). So if it looks like it's doing things in a very weird way, that's because it is.
 */
object ImporterAPI extends RestHelper with Loggable {

  case class TransactionsToInsert(l : List[ImporterTransaction])
  case class InsertedTransactions(l : List[Transaction])

  //models the json format -> This model cannot change or it will break the api call!
  //if you want to update/modernize the json format, you will need to create a new api call
  //and leave these models untouched until the old api call is no longer used by any clients
  case class ImporterTransaction(obp_transaction : ImporterOBPTransaction)
  case class ImporterOBPTransaction(this_account : ImporterAccount, other_account : ImporterAccount, details : ImporterDetails)
  case class ImporterAccount(holder : String, number : String, kind : String, bank : ImporterBank)

  /**
   * it doesn't make sense that the IBAN attribute is on the Bank (as IBAN is in reality a property of an account),
   * but this was how it was originally written and is also the
   */
  case class ImporterBank(IBAN : String, national_identifier : String, name : String)

  case class ImporterDetails(kind : String, posted : ImporterDate,
                             completed : ImporterDate, new_balance : ImporterAmount, value : ImporterAmount,
                             label : String)
  case class ImporterDate(`$dt` : Date) //format : "2012-01-04T18:06:22.000Z" (see tests)
  case class ImporterAmount(currency : String, amount : String)

  def errorJsonResponse(message : String = "error", httpCode : Int = 400) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), Nil, Nil, httpCode)

  /**
   * Legacy format
   *
   * TODO: can we just get rid of this? is anything using it?
   */
  def whenAddedJson(t : Transaction) : JObject = {

    def formatDate(date : Date) : String = {
      DefaultFormats.lossless.dateFormat.format(date)
    }

    val thisBank = Connector.connector.vend.getBank(t.bankId)
    val thisAcc = Connector.connector.vend.getBankAccount(t.bankId, t.accountId)
    val thisAccJson = JObject(List(JField("holder",
      JObject(List(
        JField("holder", JString(thisAcc.map(_.accountHolder).getOrElse(""))),
        JField("alias", JString("no"))))),
      JField("number", JString(thisAcc.map(_.number).getOrElse(""))),
      JField("kind", JString(thisAcc.map(_.accountType).getOrElse(""))),
      JField("bank", JObject(List( JField("IBAN", JString(thisAcc.flatMap(_.iban).getOrElse(""))),
        JField("national_identifier", JString(thisBank.map(_.nationalIdentifier).getOrElse(""))),
        JField("name", JString(thisBank.map(_.fullName).getOrElse(""))))))))

    val otherAcc = t.otherAccount
    val otherAccJson = JObject(List(JField("holder",
      JObject(List(
        JField("holder", JString(otherAcc.label)),
        JField("alias", JString("no"))))),
      JField("number", JString(otherAcc.number)),
      JField("kind", JString(otherAcc.kind)),
      JField("bank", JObject(List( JField("IBAN", JString(otherAcc.iban.getOrElse(""))),
        JField("national_identifier", JString(otherAcc.nationalIdentifier)),
        JField("name", JString(otherAcc.bankName)))))))

    val detailsJson = JObject(List( JField("type_en", JString(t.transactionType)),
      JField("type", JString(t.transactionType)),
      JField("posted", JString(formatDate(t.startDate))),
      JField("completed", JString(formatDate(t.finishDate))),
      JField("other_data", JString("")),
      JField("new_balance", JObject(List( JField("currency", JString(t.currency)),
        JField("amount", JString(t.balance.toString))))),
      JField("value", JObject(List( JField("currency", JString(t.currency)),
        JField("amount", JString(t.amount.toString)))))))

    val transactionJson = {
      JObject(List(JField("obp_transaction_uuid", JString(t.uuid)),
        JField("this_account", thisAccJson),
        JField("other_account", otherAccJson),
        JField("details", detailsJson)))
    }

    JObject(
      List(
        JField("obp_transaction", transactionJson)
      )
    )
  }

  serve {
    
    case "obp_transactions_saver" :: "api" :: "transactions" :: Nil JsonPost json => {

      def savetransactions ={

        def updateBankAccountBalance(insertedTransactions : List[Transaction]) = {
          if(insertedTransactions.nonEmpty) {
            //we assume here that all the Envelopes concern only one account
            val mostRecentTransaction = insertedTransactions.maxBy(t => t.finishDate)

            Connector.connector.vend.updateAccountBalance(
              mostRecentTransaction.bankId,
              mostRecentTransaction.accountId,
              mostRecentTransaction.balance)
          }
        }

        val ipAddress = json._2.remoteAddr

        val rawTransactions = json._1.children

        logger.info("Received " + rawTransactions.size +
          " json transactions to insert from ip address " + ipAddress)

        //importer api expects lossless dates
        val losslessFormats =  net.liftweb.json.DefaultFormats.lossless
        val mf = implicitly[Manifest[ImporterTransaction]]
        val importerTransactions = rawTransactions.flatMap(j => j.extractOpt[ImporterTransaction](losslessFormats, mf))

        logger.info("Received " + importerTransactions.size +
          " valid json transactions to insert from ip address " + ipAddress)

        if(importerTransactions.isEmpty) logger.warn("no transactions found to insert")

        val toInsert = TransactionsToInsert(importerTransactions)

        /**
         * Using an actor to do insertions avoids concurrency issues with
         * duplicate transactions by processing transaction batches one
         * at a time. We'll have to monitor this to see if non-concurrent I/O
         * is too inefficient. If it is, we could break it up into one actor
         * per "Account".
         */
        // TODO: this duration limit should be fixed
        val createdEnvelopes = TransactionInserter !? (3 minutes, toInsert)

        createdEnvelopes match {
          case Full(inserted : InsertedTransactions) =>
            val insertedTs = inserted.l
            logger.info("inserted " + insertedTs.size + " transactions")
            updateBankAccountBalance(insertedTs)
            val jsonList = insertedTs.map(whenAddedJson)
            JsonResponse(JArray(jsonList))
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