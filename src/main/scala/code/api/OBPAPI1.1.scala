/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v1_1

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST._
import net.liftweb.common.{Failure,Full,Empty, Box, Loggable}
import net.liftweb.mongodb._
import com.mongodb.casbah.Imports._
import _root_.java.math.MathContext
import org.bson.types._
import org.joda.time.DateTimeZone
import java.util.regex.Pattern
import _root_.net.liftweb.util._
import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.sitemap._
import _root_.scala.xml._
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.util.Helpers._
import net.liftweb.mongodb.Limit
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.mapper.view._
import code.model._
import java.util.Date
import code.api.OAuthHandshake._
import code.bankconnectors.{OBPOrder, OBPLimit, OBPOffset, OBPOrdering, OBPFromDate, OBPToDate, OBPQueryParam}
import java.net.URL
import code.metrics.{APIMetrics}

case class TagJSON(
  value : String,
  posted_date : Date
)

case class NarrativeJSON(
  narrative : String
)

case class CommentJSON(
  value : String,
  posted_date : Date
)

case class ImageJSON(
  URL : String,
  label : String
)
case class MoreInfoJSON(
  more_info : String
)
case class UrlJSON(
  URL : String
)
case class ImageUrlJSON(
  image_URL : String
)
case class OpenCorporatesUrlJSON(
  open_corporates_url : String
)
case class WhereTagJSON(
  where : GeoCord
)
case class CorporateLocationJSON(
  corporate_location : GeoCord
)
case class PhysicalLocationJSON(
  physical_location : GeoCord
)
case class GeoCord(
  longitude : Double,
  latitude : Double
)
case class ErrorMessage(
  error : String
)

case class SuccessMessage(
  success : String
)

object OBPAPI1_1 extends RestHelper with Loggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  implicit def successToJson(success: SuccessMessage): JValue = Extraction.decompose(success)

  private def httpMethod : String =
    S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }

  private def getUser(httpCode : Int, tokenID : Box[String]) : Box[User] =
  if(httpCode==200)
  {
    import code.model.Token
    logger.info("OAuth header correct ")
    Token.find(By(Token.key, tokenID.get)) match {
      case Full(token) => {
        logger.info("access token: "+ token + " found")
        val user = User.findByApiId(token.userForeignKey.get.toString)
        //just a log
        user match {
          case Full(u) => logger.info("user " + u.emailAddress + " was found from the oauth token")
          case _ => logger.info("no user was found for the oauth token")
        }
        user
      }
      case _ =>{
        logger.warn("no token " + tokenID.get + " found")
        Empty
      }
    }
  }
  else
    Empty

  private def isThereAnOAuthHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("OAuth")
        case _ => false
      }
      case _ => false
    }
  }

  private def logAPICall =
    APIMetrics.apiMetrics.vend.saveMetric(S.uriAndQueryString.getOrElse(""), (now: TimeSpan))

  private def isFieldAlreadySet(field : String) : Box[String] =
    if(field.isEmpty)
     Full(field)
    else
      Failure("field already set, use PUT method to update it")

  private def transactionJson(t : ModeratedTransaction) : JObject = {
    ("transaction" ->
      ("uuid" -> t.UUID) ~
      ("id" -> t.id.value) ~
      ("this_account" -> t.bankAccount.map(thisAccountJson)) ~
      ("other_account" -> t.otherBankAccount.map(otherAccountToJson)) ~
      ("details" ->
        ("type" -> t.transactionType.getOrElse("")) ~
        ("label" -> t.description.getOrElse("")) ~
        ("posted" -> t.dateOption2JString(t.startDate)) ~
        ("completed" -> t.dateOption2JString(t.finishDate)) ~
        ("new_balance" ->
          ("currency" -> t.currency.getOrElse("")) ~
          ("amount" -> t.balance)) ~
        ("value" ->
          ("currency" -> t.currency.getOrElse("")) ~
          ("amount" -> t.amount))))
  }

  private def thisAccountJson(thisAccount : ModeratedBankAccount) : JObject = {
    ("holder" -> thisAccount.owners.getOrElse(Set()).map(ownerJson)) ~
    ("number" -> thisAccount.number.getOrElse("")) ~
    ("kind" -> thisAccount.accountType.getOrElse("")) ~
    ("bank" ->
      ("IBAN" -> thisAccount.iban.getOrElse("")) ~
      ("national_identifier" -> thisAccount.nationalIdentifier.getOrElse("")) ~
      ("name" -> thisAccount.bankName.getOrElse(""))
    )
  }

  private def ownerJson(owner : User) : JObject = {
    ("name" -> owner.name) ~
    ("is_alias" -> false)
  }

  private def otherAccountToJson(otherAccount : ModeratedOtherBankAccount) : JObject = {
    ("holder" ->
      ("name" -> otherAccount.label.display) ~
      ("is_alias" -> otherAccount.isAlias)
    ) ~
    ("number" -> otherAccount.number.getOrElse("")) ~
    ("kind" -> otherAccount.kind.getOrElse("")) ~
    ("bank" ->
      ("IBAN" -> otherAccount.iban.getOrElse("")) ~
      ("national_identifier" -> otherAccount.nationalIdentifier.getOrElse("")) ~
      ("name" -> otherAccount.bankName.getOrElse(""))
    )
  }

  private def userToJson(user : Box[User]) : JValue =
    user match {
      case Full(u) =>
              ("id" -> u.idGivenByProvider) ~
              ("provider" -> u.provider ) ~
              ("display_name" -> u.name)

      case _ => ("id" -> "") ~
                ("provider" -> "") ~
                ("display_name" -> "")
    }

  private def oneFieldJson(key : String, value : String) : JObject =
    (key -> value)

  private def geoTagToJson(name : String, geoTag : Option[GeoTag]) : JValue = {
    geoTag match {
      case Some(tag) =>
      (name ->
        ("latitude" -> tag.latitude) ~
        ("longitude" -> tag.longitude) ~
        ("date" -> tag.datePosted.toString) ~
        ("user" -> userToJson(tag.postedBy))
      )
      case _ => ""
    }
  }

  private def moderatedTransactionMetadata(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : Box[ModeratedTransactionMetadata] =
    for {
      account <- BankAccount(bankId, accountId) ?~ { "bank " + bankId + " and account "  + accountId + " not found for bank"}
      view <- View.fromUrl(viewId, account) ?~ { "view "  + viewId + " not found"}
      moderatedTransaction <- account.moderatedTransaction(transactionId, view, user) ?~ "view/transaction not authorized"
      metadata <- Box(moderatedTransaction.metadata) ?~ {"view " + viewId + " does not authorize metadata access"}
    } yield metadata

  private def moderatedTransactionOtherAccount(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : Box[ModeratedOtherBankAccount] =
    for {
      account <- BankAccount(bankId, accountId) ?~ { "bank " + bankId + " and account "  + accountId + " not found for bank"}
      view <- View.fromUrl(viewId, account) ?~ { "view "  + viewId + " not found"}
      moderatedTransaction <- account.moderatedTransaction(transactionId, view, user) ?~ "view/transaction not authorized"
      otherAccount <- Box(moderatedTransaction.otherBankAccount) ?~ {"view " + viewId + " does not authorize other account access"}
    } yield otherAccount

  private def moderatedOtherAccount(bankId : BankId, accountId : AccountId, viewId : String, other_account_ID : String, user : Box[User]) : Box[ModeratedOtherBankAccount] =
    for {
      account <- BankAccount(bankId, accountId) ?~ { "bank " + bankId + " and account "  + accountId + " not found for bank"}
      view <- View.fromUrl(viewId, account) ?~ { "view "  + viewId + " not found"}
      moderatedOtherBankAccount <- account.moderatedOtherBankAccount(other_account_ID, view, user)
    } yield moderatedOtherBankAccount

  private def moderatedOtherAccountMetadata(bankId : BankId, accountId : AccountId, viewId : String, other_account_ID : String, user : Box[User]) : Box[ModeratedOtherBankAccountMetadata] =
    for {
      moderatedOtherBankAccount <- moderatedOtherAccount(bankId, accountId, viewId, other_account_ID, user)
      metadata <- Box(moderatedOtherBankAccount.metadata) ?~! {"view " + viewId + "does not allow other bank account metadata access"}
    } yield metadata

  serve("obp" / "v1.1" prefix {

    case Nil JsonGet json => {
      logAPICall

      def gitCommit : String = {
        val commit = tryo{
          val properties = new java.util.Properties()
          properties.load(getClass().getClassLoader().getResourceAsStream("git.properties"))
          properties.getProperty("git.commit.id", "")
        }
        commit getOrElse ""
      }

      val apiDetails = {
        ("api" ->
          ("version" -> "1.1") ~
          ("git_commit" -> gitCommit) ~
          ("hosted_by" ->
            ("organisation" -> "TESOBE") ~
            ("email" -> "contact@tesobe.com") ~
            ("phone" -> "+49 (0)30 8145 3994"))) ~
        ("links" ->
          ("rel" -> "banks") ~
          ("href" -> "/banks") ~
          ("method" -> "GET") ~
          ("title" -> "Returns a list of banks supported on this server"))
      }

      JsonResponse(apiDetails)
    }

    case "banks" :: Nil JsonGet json => {
      logAPICall
      def bankToJson( b : Bank) = {
        ("bank" ->
          ("id" -> b.id.value) ~
          ("short_name" -> b.shortName) ~
          ("full_name" -> b.fullName) ~
          ("logo" -> b.logoURL) ~
          ("website" -> b.website)
        )
      }

      JsonResponse("banks" -> Bank.all.map(bankToJson _ ))
    }

  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: Nil JsonGet json => {
      logAPICall

      def bankToJson( b : Bank) = {
        ("bank" ->
          ("id" -> b.id.value) ~
          ("short_name" -> b.shortName) ~
          ("full_name" -> b.fullName) ~
          ("logo" -> b.logoURL) ~
          ("website" -> b.website)
        )
      }

      for {
        b <- Bank(bankId)
      } yield JsonResponse(bankToJson(b))
    }
  })

  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
      val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

      def viewToJson(v : View) : JObject = {
        ("view" -> (
            ("id" -> v.permalink) ~
            ("short_name" -> v.name) ~
            ("description" -> v.description) ~
            ("is_public" -> v.isPublic)
        ))
      }

      def accountToJson(acc : BankAccount, user : Box[User]) : JObject = {
        //just a log
        user match {
          case Full(u) => logger.info("user " + u.emailAddress + " was found")
          case _ => logger.info("no user was found")
        }

        val views = acc permittedViews user
        ("account" -> (
          ("id" -> acc.accountId.value) ~
          ("views_available" -> views.map(viewToJson(_)))
        ))
      }
      def bankAccountSet2JsonResponse(bankAccounts: Set[BankAccount]): LiftResponse = {
        val accJson = bankAccounts.map(accountToJson(_,user))
        JsonResponse(("accounts" -> accJson))
      }

      Bank(bankId) match {
        case Full(bank) =>
        {
          if(isThereAnOAuthHeader)
          {
            if(httpCode == 200)
            {
              bank.accountv12AndBelow(user) match {
                case Full(a) =>  bankAccountSet2JsonResponse(a.toSet)
                case _ => InMemoryResponse("no account found".getBytes, Nil, Nil, 404)
              }
            }
            else
              JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
          }
          else
          {
            bank.accountv12AndBelow(user) match {
              case Full(a) =>  bankAccountSet2JsonResponse(a.toSet)
              case _ => InMemoryResponse("no account found".getBytes, Nil, Nil, 404)
            }
          }
        }
        case _ =>  {
          val error = "bank " + bankId + " not found"
          JsonResponse(ErrorMessage(error), Nil, Nil, httpCode)
        }
      }
    }
  })

  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "account" :: Nil JsonGet json => {
      logAPICall
      val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
      val user = getUser(httpCode, oAuthParameters.get("oauth_token"))

      case class ModeratedAccountAndViews(account: ModeratedBankAccount, views: List[View])

      val moderatedAccountAndViews = for {
        bank <- Bank(bankId) ?~ { "bank " + bankId + " not found" } ~> 404
        account <- BankAccount(bankId, accountId) ?~ { "account " + accountId + " not found for bank" } ~> 404
        view <- View.fromUrl(viewId, account) ?~ { "view " + viewId + " not found for account" } ~> 404
        moderatedAccount <- account.moderatedBankAccount(view, user) ?~ { "view/account not authorized" } ~> 401
        availableViews <- Full(account.permittedViews(user))
      } yield ModeratedAccountAndViews(moderatedAccount, availableViews)

      val bankName = moderatedAccountAndViews.flatMap(_.account.bankName) getOrElse ""

      def viewJson(view: View): JObject = {

        ("id" -> view.id) ~
        ("short_name" -> view.name) ~
        ("description" -> view.description) ~
        ("is_public" -> view.isPublic)
      }

      def ownerJson(accountOwner: User): JObject = {
        ("user_id" -> accountOwner.idGivenByProvider) ~
        ("user_provider" -> bankName) ~
        ("display_name" -> accountOwner.name)
      }

      def balanceJson(account: ModeratedBankAccount): JObject = {
        ("currency" -> account.currency.getOrElse("")) ~
        ("amount" -> account.balance)
      }

      def json(account: ModeratedBankAccount, views: List[View]): JObject = {
        ("account" ->
          ("number" -> account.number.getOrElse("")) ~
          ("owners" -> account.owners.getOrElse(Set()).map(ownerJson)) ~
          ("type" -> account.accountType.getOrElse("")) ~
          ("balance" -> balanceJson(account)) ~
          ("IBAN" -> account.iban.getOrElse("")) ~
          ("views_available" -> views.map(viewJson))
        )
      }
      if(isThereAnOAuthHeader)
      {
        if(httpCode == 200)
          moderatedAccountAndViews.map(mv => JsonResponse(json(mv.account, mv.views)))
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
      	moderatedAccountAndViews.map(mv => JsonResponse(json(mv.account, mv.views)))
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: Nil JsonGet json => {
      import code.api.v1_2_1.APIMethods121.getTransactionParams

      //log the API call
      logAPICall

      val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil

      def transactionsJson(transactions : List[ModeratedTransaction], v : View) : JObject = {
        ("transactions" -> transactions.map(transactionJson))
      }
      val response : Box[JsonResponse] = for {
        params <- getTransactionParams(json)
        bankAccount <- BankAccount(bankId, accountId)
        view <- View.fromUrl(viewId, bankAccount)
        transactions <- bankAccount.getModeratedTransactions(getUser(httpCode,oAuthParameters.get("oauth_token")), view, params: _*)
      } yield {
        JsonResponse(transactionsJson(transactions, view),Nil, Nil, 200)
      }

      response getOrElse (JsonResponse(ErrorMessage(message), Nil, Nil, 401)) : LiftResponse
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def transactionInJson(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        val moderatedTransaction = for {
            account <- BankAccount(bankId, accountId) ?~ { "bank " + bankId + " and account "  + accountId + " not found for bank"}
            view <- View.fromUrl(viewId, account) ?~ { "view "  + viewId + " not found"}
            moderatedTransaction <- account.moderatedTransaction(transactionId, view, user) ?~ "view/transaction not authorized"
          } yield moderatedTransaction

          moderatedTransaction match {
            case Full(transaction) => JsonResponse(transactionJson(transaction), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
          transactionInJson(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        transactionInJson(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def narrativeInJson(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        val narrative = for {
            metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
            narrative <- Box(metadata.ownerComment) ?~ {"view " + viewId + " does not authorize narrative access"}
          } yield narrative

          narrative match {
            case Full(narrative) => JsonResponse(oneFieldJson("narrative", narrative), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
          narrativeInJson(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        narrativeInJson(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[NarrativeJSON]
          } match {
            case Full(narrativeJson) => {

              val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

              val addNarrativeFunc = for {
                  metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
                  narrative <- Box(metadata.ownerComment) ?~ {"view " + viewId + " does not authorize narrative access"}
                  narrativeSetted <- isFieldAlreadySet(narrative)
                  addNarrativeFunc <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not authorize narrative edit"}
                } yield addNarrativeFunc

              addNarrativeFunc match {
                case Full(addNarrative) => {
                  addNarrative(narrativeJson.narrative)
                  JsonResponse(SuccessMessage("narrative successfully saved"), Nil, Nil, 201)
                }
                case Failure(msg,_,_) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[NarrativeJSON]
          } match {
            case Full(narrativeJson) => {

              val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

              val addNarrativeFunc = for {
                  metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
                  narrative <- Box(metadata.ownerComment) ?~ {"view " + viewId + " does not authorize narrative access"}
                  addNarrativeFunc <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not authorize narrative edit"}
                } yield addNarrativeFunc

              addNarrativeFunc match {
                case Full(addNarrative) => {
                  addNarrative(narrativeJson.narrative)
                  JsonResponse(SuccessMessage("narrative successfully saved"), Nil, Nil, 201)
                }
                case Failure(msg,_,_) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def commentToJson(comment : code.model.Comment) : JValue = {
        ("comment" ->
          ("id" -> comment.id_) ~
          ("date" -> comment.datePosted.toString) ~
          ("value" -> comment.text) ~
          ("user" -> userToJson(comment.postedBy)) ~
          ("reply_to" -> comment.replyToID)
        )
      }

      def commentsToJson(comments : List[code.model.Comment]) : JValue = {
        ("comments" -> comments.map(commentToJson))
      }

      def commentsResponce(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        val comments = for {
            metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
            comments <- Box(metadata.comments) ?~ {"view " + viewId + " does not authorize comments access"}
          } yield comments

          comments match {
            case Full(commentsList) => JsonResponse(commentsToJson(commentsList), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
          commentsResponce(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        commentsResponce(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[CommentJSON]
          } match {
            case Full(commentJson) => {
              def addComment(user : User, viewID : Long, text: String, datePosted : Date) = {
                val addComment = for {
                  metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,Full(user))
                  addCommentFunc <- Box(metadata.addComment) ?~ {"view " + viewId + " does not authorize adding comment"}
                } yield addCommentFunc

                addComment.map(
                  func =>{
                    func(user.apiId, viewID, text, datePosted)
                    Full(text)
                  }
                )
              }

              val comment = for{
                  user <- getUser(httpCode,oAuthParameters.get("oauth_token")) ?~ "User not found. Authentication via OAuth is required"
                  view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                  postedComment <- addComment(user, view.id, commentJson.value, commentJson.posted_date)
                } yield postedComment

              comment match {
                case Full(text) => JsonResponse(SuccessMessage("comment : " + text + "successfully saved"), Nil, Nil, 201)
                case Failure(msg, _, _) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def tagToJson(tag : TransactionTag) : JValue = {
        ("tag" ->
          ("id" -> tag.id_) ~
          ("date" -> tag.datePosted.toString) ~
          ("value" -> tag.value) ~
          ("user" -> userToJson(tag.postedBy))
        )
      }

      def tagsToJson(tags : List[TransactionTag]) : JValue = {
        ("tags" -> tags.map(tagToJson))
      }

      def tagsResponce(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        val tags = for {
            metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
            tags <- Box(metadata.tags) ?~ {"view " + viewId + " does not authorize tags access"}
          } yield tags

          tags match {
            case Full(tagsList) => JsonResponse(tagsToJson(tagsList), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
          tagsResponce(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        tagsResponce(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix {
    //post a tag
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[TagJSON]
          } match {
            case Full(tagJson) => {
              if(! tagJson.value.contains(" "))
              {
                def addTag(user : User, viewID : Long, tag: String, datePosted : Date) = {
                  val addTag = for {
                    metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,Full(user))
                    addTagFunc <- Box(metadata.addTag) ?~ {"view " + viewId + " does not authorize adding comment"}
                  } yield addTagFunc

                  addTag.map(
                    func =>{
                      Full(func(user.apiId, viewID, tag, datePosted))
                    }
                  )
                }

                val tag = for{
                    user <- getUser(httpCode,oAuthParameters.get("oauth_token")) ?~ "User not found. Authentication via OAuth is required"
                    view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                    postedTagID <- addTag(user, view.id, tagJson.value, tagJson.posted_date)
                  } yield postedTagID

                tag match {
                  case Full(postedTagID) => JsonResponse(SuccessMessage("tag : " + postedTagID + "successfully saved"), Nil, Nil, 201)
                  case Failure(msg, _, _) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                  case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
                }
              }
              else
              {
                JsonResponse(ErrorMessage("tag value MUST NOT contain a white space"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def imageToJson(image : TransactionImage) : JValue = {
        ("image" ->
          ("id" -> image.id_) ~
          ("label" -> image.description) ~
          ("URL" -> image.imageUrl.toString) ~
          ("date" -> image.datePosted.toString) ~
          ("user" -> userToJson(image.postedBy))
        )
      }

      def imagesToJson(images : List[TransactionImage]) : JValue = {
        ("images" -> images.map(imageToJson))
      }

      def imagesResponce(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        val images = for {
            metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
            images <- Box(metadata.images) ?~ {"view " + viewId + " does not authorize tags access"}
          } yield images

          images match {
            case Full(imagesList) => JsonResponse(imagesToJson(imagesList), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
          imagesResponce(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        imagesResponce(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix {
    //post an image
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[ImageJSON]
          } match {
            case Full(imageJson) => {
              def addImage(user : User, viewID : Long, label: String, url : URL) : Box[String] = {
                val addImage = for {
                  metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,Full(user))
                  addImageFunc <- Box(metadata.addImage) ?~ {"view " + viewId + " does not authorize adding comment"}
                } yield addImageFunc

                addImage.flatMap(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, label, datePosted, url).map(_.id_)
                  }
                )
              }

              val imageId = for{
                  user <- getUser(httpCode,oAuthParameters.get("oauth_token")) ?~ "User not found. Authentication via OAuth is required"
                  view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                  url <- tryo{new URL(imageJson.URL)} ?~! "Could not parse url string as a valid URL"
                  postedImageId <- addImage(user, view.id, imageJson.label, url)
                } yield postedImageId

              imageId match {
                case Full(postedImageId) => JsonResponse(SuccessMessage("image : " + postedImageId + "successfully saved"), Nil, Nil, 201)
                case Failure(msg, _, _) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def whereTagResponce(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        val whereTag = for {
            metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,user)
            whereTag <- Box(metadata.whereTag) ?~ {"view " + viewId + " does not authorize tags access"}
          } yield whereTag

          whereTag match {
            case Full(whereTag) => JsonResponse(geoTagToJson("where", whereTag), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          whereTagResponce(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        whereTagResponce(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[WhereTagJSON]
          } match {
            case Full(whereTagJson) => {
              def addWhereTag(user : User, viewID : Long, longitude: Double, latitude : Double) : Box[Boolean] = {
                val addWhereTag = for {
                  metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,Full(user))
                  addWhereTagFunc <- Box(metadata.addWhereTag) ?~ {"view " + viewId + " does not authorize adding where tag"}
                } yield addWhereTagFunc

                addWhereTag.map(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, datePosted, longitude, latitude)
                  }
                )
              }

              val postedGeoTag = for{
                  user <- getUser(httpCode,oAuthParameters.get("oauth_token")) ?~ "User not found. Authentication via OAuth is required"
                  view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                  posterWheteTag <- addWhereTag(user, view.id, whereTagJson.where.longitude, whereTagJson.where.latitude)
                } yield posterWheteTag

              postedGeoTag match {
                case Full(postedWhereTag) =>
                  if(postedWhereTag)
                    JsonResponse(SuccessMessage("Geo tag successfully saved"), Nil, Nil, 201)
                  else
                    JsonResponse(ErrorMessage("Geo tag could not be saved"), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
          tryo{
            json.extract[WhereTagJSON]
          } match {
            case Full(whereTagJson) => {
              def addWhereTag(user : User, viewID : Long, longitude: Double, latitude : Double) : Box[Boolean] = {
                val addWhereTag = for {
                  metadata <- moderatedTransactionMetadata(bankId,accountId,viewId,transactionId,Full(user))
                  addWhereTagFunc <- Box(metadata.addWhereTag) ?~ {"view " + viewId + " does not authorize adding where tag"}
                } yield addWhereTagFunc

                addWhereTag.map(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, datePosted, longitude, latitude)
                  }
                )
              }

              val postedGeoTag = for{
                  user <- getUser(httpCode,oAuthParameters.get("oauth_token")) ?~ "User not found. Authentication via OAuth is required"
                  view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                  posterWheteTag <- addWhereTag(user, view.id, whereTagJson.where.longitude, whereTagJson.where.latitude)
                } yield posterWheteTag

              postedGeoTag match {
                case Full(postedWhereTag) =>
                  if(postedWhereTag)
                    JsonResponse(SuccessMessage("Geo tag successfully saved"), Nil, Nil, 201)
                  else
                    JsonResponse(ErrorMessage("Geo tag could not be saved"), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(ErrorMessage(msg), Nil, Nil, 400)
                case _ => JsonResponse(ErrorMessage("error"), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(ErrorMessage("wrong JSON format"), Nil, Nil, 400)
          }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "transactions" :: TransactionId(transactionId) :: "other_account" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def otherAccountToJson(otherAccount : ModeratedOtherBankAccount) : JObject = {
        ("id" -> otherAccount.id) ~
        ("number" -> otherAccount.number.getOrElse("")) ~
        ("holder" ->
          ("name" -> otherAccount.label.display) ~
          ("is_alias" -> otherAccount.isAlias)
        ) ~
        ("national_identifier" -> otherAccount.nationalIdentifier.getOrElse("")) ~
        ("IBAN" -> otherAccount.iban.getOrElse("")) ~
        ("bank_name" -> otherAccount.bankName.getOrElse("")) ~
        ("swift_bic" -> otherAccount.swift_bic.getOrElse(""))
      }

      def otherAccountResponce(bankId : BankId, accountId : AccountId, viewId : String, transactionId : TransactionId, user : Box[User]) : JsonResponse = {
        moderatedTransactionOtherAccount(bankId,accountId,viewId,transactionId,user) match {
            case Full(otherAccount) => JsonResponse(otherAccountToJson(otherAccount), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          otherAccountResponce(bankId, accountId, viewId, transactionId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        otherAccountResponce(bankId, accountId, viewId, transactionId, None)
    }
  })
  serve("obp" / "v1.1" prefix {
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: other_account_ID :: "metadata" :: Nil JsonGet json => {
      //log the API call
      logAPICall

      def otherAccountMetadataToJson(metadata : ModeratedOtherBankAccountMetadata) : JObject = {
        ("more_info" -> metadata.moreInfo.getOrElse("")) ~
        ("URL" -> metadata.url.getOrElse("")) ~
        ("image_URL" -> metadata.imageURL.getOrElse("")) ~
        ("open_corporates_URL" -> metadata.openCorporatesURL.getOrElse("")) ~
        ("corporate_location" -> metadata.corporateLocation.map(l => geoTagToJson("corporate_location",l))) ~
        ("physical_location" -> metadata.physicalLocation.map(l => geoTagToJson("physical_location",l)))
      }

      def otherAccountMetadataResponce(bankId : BankId, accountId : AccountId, viewId : String, other_account_ID : String, user : Box[User]) : JsonResponse = {
        val otherAccountMetaData = for{
          otherAccount <- moderatedOtherAccount(bankId, accountId, viewId, other_account_ID, user)
          metaData <- Box(otherAccount.metadata) ?~! {"view " + viewId + "does not allow other account metadata access" }
        } yield metaData

        otherAccountMetaData match {
            case Full(metadata) => JsonResponse(otherAccountMetadataToJson(metadata), Nil, Nil, 200)
            case Failure(msg,_,_) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
          }
      }

      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          otherAccountMetadataResponce(bankId, accountId, viewId, other_account_ID, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, 400)
      }
      else
        otherAccountMetadataResponce(bankId, accountId, viewId, other_account_ID, None)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "more_info" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      def postMoreInfoResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[MoreInfoJSON]
          } match {
            case Full(moreInfoJson) => {

              def addMoreInfo(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], moreInfo : String): Box[Boolean] = {
                val addMoreInfo = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  moreInfo <- Box(metadata.moreInfo) ?~! {"view " + viewId + " does not authorize access to more_info"}
                  setMoreInfo <- isFieldAlreadySet(moreInfo)
                  addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"view " + viewId + " does not authorize adding more_info"}
                } yield addMoreInfo

                addMoreInfo.map(
                  func =>{
                    func(moreInfo)
                  }
                )
              }

              addMoreInfo(bankId, accountId, viewId, otherAccountId, user, moreInfoJson.more_info) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("more info successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("more info could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postMoreInfoResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        postMoreInfoResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "more_info" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      def updateMoreInfoResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[MoreInfoJSON]
          } match {
            case Full(moreInfoJson) => {

              def addMoreInfo(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], moreInfo : String): Box[Boolean] = {
                val addMoreInfo = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"view " + viewId + " does not authorize adding more_info"}
                } yield addMoreInfo

                addMoreInfo.map(
                  func =>{
                    func(moreInfo)
                  }
                )
              }

              addMoreInfo(bankId, accountId, viewId, otherAccountId, user, moreInfoJson.more_info) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("more info successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("more info could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          updateMoreInfoResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        updateMoreInfoResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "url" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      def postURLResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[UrlJSON]
          } match {
            case Full(urlJson) => {

              def addUrl(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], url : String): Box[Boolean] = {
                val addUrl = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  url <- Box(metadata.url) ?~! {"view " + viewId + " does not authorize access to URL"}
                  setUrl <- isFieldAlreadySet(url)
                  addUrl <- Box(metadata.addURL) ?~ {"view " + viewId + " does not authorize adding URL"}
                } yield addUrl

                addUrl.map(
                  func =>{
                    func(url)
                  }
                )
              }

              addUrl(bankId, accountId, viewId, otherAccountId, user, urlJson.URL) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("URL successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("URL could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postURLResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        postURLResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "url" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      def updateURLResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[UrlJSON]
          } match {
            case Full(urlJson) => {

              def addUrl(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], url : String): Box[Boolean] = {
                val addUrl = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  addUrl <- Box(metadata.addURL) ?~ {"view " + viewId + " does not authorize adding URL"}
                } yield addUrl

                addUrl.map(
                  func =>{
                    func(url)
                  }
                )
              }

              addUrl(bankId, accountId, viewId, otherAccountId, user, urlJson.URL) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("URL successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("URL could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          updateURLResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        updateURLResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "image_url" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      def postImageUrlResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[ImageUrlJSON]
          } match {
            case Full(imageUrlJson) => {

              def addImageUrl(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], url : String): Box[Boolean] = {
                val addImageUrl = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  imageUrl <- Box(metadata.imageURL) ?~! {"view " + viewId + " does not authorize access to image URL"}
                  setImageUrl <- isFieldAlreadySet(imageUrl)
                  addImageUrl <- Box(metadata.addImageURL) ?~ {"view " + viewId + " does not authorize adding image URL"}
                } yield addImageUrl

                addImageUrl.map(
                  func =>{
                    func(url)
                  }
                )
              }

              addImageUrl(bankId, accountId, viewId, otherAccountId, user, imageUrlJson.image_URL) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("Image URL successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("Image URL could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postImageUrlResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        postImageUrlResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "image_url" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      def updateImageUrlResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[ImageUrlJSON]
          } match {
            case Full(imageUrlJson) => {

              def addImageUrl(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], url : String): Box[Boolean] = {
                val addImageUrl = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  addImageUrl <- Box(metadata.addImageURL) ?~ {"view " + viewId + " does not authorize adding image URL"}
                } yield addImageUrl

                addImageUrl.map(
                  func =>{
                    func(url)
                  }
                )
              }

              addImageUrl(bankId, accountId, viewId, otherAccountId, user, imageUrlJson.image_URL) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("Image URL successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("Image URL could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          updateImageUrlResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        updateImageUrlResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "open_corporates_url" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      def postOpenCorporatesUrlResponce(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[OpenCorporatesUrlJSON]
          } match {
            case Full(openCorporatesUrlJSON) => {

              def addOpenCorporatesUrl(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], url : String): Box[Boolean] = {
                val addOpenCorporatesUrl = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  openCorporatesUrl <- Box(metadata.openCorporatesURL) ?~! {"view " + viewId + " does not authorize access to open_corporates_url"}
                  setImageUrl <- isFieldAlreadySet(openCorporatesUrl)
                  addOpenCorporatesUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"view " + viewId + " does not authorize adding open_corporates_url"}
                } yield addOpenCorporatesUrl

                addOpenCorporatesUrl.map(
                  func =>{
                    func(url)
                  }
                )
              }

              addOpenCorporatesUrl(bankId, accountId, viewId, otherAccountId, user, openCorporatesUrlJSON.open_corporates_url) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("open_corporates_url successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("open_corporates_url could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postOpenCorporatesUrlResponce(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        postOpenCorporatesUrlResponce(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "open_corporates_url" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      def updateOpenCorporatesURL(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[OpenCorporatesUrlJSON]
          } match {
            case Full(openCorporatesUrlJSON) => {

              def addOpenCorporatesUrl(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId : String, user : Box[User], url : String): Box[Boolean] = {
                val addOpenCorporatesUrl = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,user)
                  addOpenCorporatesUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"view " + viewId + " does not authorize adding open_corporates_url"}
                } yield addOpenCorporatesUrl

                addOpenCorporatesUrl.map(
                  func =>{
                    func(url)
                  }
                )
              }

              addOpenCorporatesUrl(bankId, accountId, viewId, otherAccountId, user, openCorporatesUrlJSON.open_corporates_url) match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("open_corporates_url successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("open_corporates_url could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          updateOpenCorporatesURL(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        updateOpenCorporatesURL(bankId, accountId, viewId, otherAccountId, Empty)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "corporate_location" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      def postCorporateLocation(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[CorporateLocationJSON]
          } match {
            case Full(corporateLocationJSON) => {

              def addCorporateLocation(user : User, viewID : Long, longitude: Double, latitude : Double) : Box[Boolean] = {
                val addCorporateLocation = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,Full(user))
                  addCorporateLocation <- Box(metadata.addCorporateLocation) ?~ {"view " + viewId + " does not authorize adding corporate_location"}
                } yield addCorporateLocation

                addCorporateLocation.map(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, datePosted, longitude, latitude)
                  }
                )
              }
              val postedGeoTag = for {
                    u <- user ?~ "User not found. Authentication via OAuth is required"
                    view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                    postedGeoTag <- addCorporateLocation(u, view.id, corporateLocationJSON.corporate_location.longitude, corporateLocationJSON.corporate_location.latitude)
                  } yield postedGeoTag

              postedGeoTag match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("corporate_location successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("corporate_location could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postCorporateLocation(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "corporate_location" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      def postCorporateLocation(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[CorporateLocationJSON]
          } match {
            case Full(corporateLocationJSON) => {

              def addCorporateLocation(user : User, viewID : Long, longitude: Double, latitude : Double) : Box[Boolean] = {
                val addCorporateLocation = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,Full(user))
                  addCorporateLocation <- Box(metadata.addCorporateLocation) ?~ {"view " + viewId + " does not authorize adding corporate_location"}
                } yield addCorporateLocation

                addCorporateLocation.map(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, datePosted, longitude, latitude)
                  }
                )
              }
              val postedGeoTag = for {
                    u <- user ?~ "User not found. Authentication via OAuth is required"
                    view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                    postedGeoTag <- addCorporateLocation(u, view.id, corporateLocationJSON.corporate_location.longitude, corporateLocationJSON.corporate_location.latitude)
                  } yield postedGeoTag

              postedGeoTag match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("corporate_location successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("corporate_location could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postCorporateLocation(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "physical_location" :: Nil JsonPost json -> _ => {
      //log the API call
      logAPICall

      def postPhysicalLocation(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[PhysicalLocationJSON]
          } match {
            case Full(physicalLocationJSON) => {

              def addPhysicalLocation(user : User, viewID : Long, longitude: Double, latitude : Double) : Box[Boolean] = {
                val addPhysicalLocation = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,Full(user))
                  addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"view " + viewId + " does not authorize adding physical_location"}
                } yield addPhysicalLocation

                addPhysicalLocation.map(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, datePosted, longitude, latitude)
                  }
                )
              }
              val postedGeoTag = for {
                    u <- user ?~ "User not found. Authentication via OAuth is required"
                    view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                    postedGeoTag <- addPhysicalLocation(u, view.id, physicalLocationJSON.physical_location.longitude, physicalLocationJSON.physical_location.latitude)
                  } yield postedGeoTag

              postedGeoTag match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("physical_location successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("physical_location could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postPhysicalLocation(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
  serve("obp" / "v1.1" prefix{
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: viewId :: "other_accounts" :: otherAccountId :: "metadata" :: "physical_location" :: Nil JsonPut json -> _ => {
      //log the API call
      logAPICall

      def postPhysicalLocation(bankId : BankId, accountId : AccountId, viewId : String, otherAccountId: String, user : Box[User]) : JsonResponse =
        tryo{
            json.extract[PhysicalLocationJSON]
          } match {
            case Full(physicalLocationJSON) => {

              def addPhysicalLocation(user : User, viewID : Long, longitude: Double, latitude : Double) : Box[Boolean] = {
                val addPhysicalLocation = for {
                  metadata <- moderatedOtherAccountMetadata(bankId,accountId,viewId,otherAccountId,Full(user))
                  addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"view " + viewId + " does not authorize adding physical_location"}
                } yield addPhysicalLocation

                addPhysicalLocation.map(
                  func =>{
                    val datePosted = (now: TimeSpan)
                    func(user.apiId, viewID, datePosted, longitude, latitude)
                  }
                )
              }
              val postedGeoTag = for {
                    u <- user ?~ "User not found. Authentication via OAuth is required"
                    view <- View.fromUrl(viewId, accountId, bankId) ?~ {"view " + viewId +" view not found"}
                    postedGeoTag <- addPhysicalLocation(u, view.id, physicalLocationJSON.physical_location.longitude, physicalLocationJSON.physical_location.latitude)
                  } yield postedGeoTag

              postedGeoTag match {
                case Full(posted) =>
                  if(posted)
                    JsonResponse(Extraction.decompose(SuccessMessage("physical_location successfully saved")), Nil, Nil, 201)
                  else
                    JsonResponse(Extraction.decompose(ErrorMessage("physical_location could not be saved")), Nil, Nil, 500)
                case Failure(msg, _, _) => JsonResponse(Extraction.decompose(ErrorMessage(msg)), Nil, Nil, 400)
                case _ => JsonResponse(Extraction.decompose(ErrorMessage("error")), Nil, Nil, 400)
              }
            }
            case _ => JsonResponse(Extraction.decompose(ErrorMessage("wrong JSON format")), Nil, Nil, 400)
          }


      if(isThereAnOAuthHeader)
      {
        val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
        if(httpCode == 200)
        {
          val user = getUser(httpCode, oAuthParameters.get("oauth_token"))
          postPhysicalLocation(bankId, accountId, viewId, otherAccountId, user)
        }
        else
          JsonResponse(ErrorMessage(message), Nil, Nil, httpCode)
      }
      else
        JsonResponse(ErrorMessage("Authentication via OAuth is required"), Nil, Nil, 400)
    }
  })
}