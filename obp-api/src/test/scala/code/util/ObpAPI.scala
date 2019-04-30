package code.util

import java.io._
import java.net.{HttpURLConnection, URL}
import java.text.SimpleDateFormat
import java.util.Date

import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.Helper.MdcLoggable
import code.util.ObpJson._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.http.RequestVar
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{JObject, _}
import net.liftweb.util.Helpers._

case class Header(key: String, value: String)
case class ObpError(error :String)

object ObpAPI extends MdcLoggable {
  implicit val formats = CustomJsonFormats.formats
  val dateFormat = APIUtil.DateWithMsRollbackFormat
  
  val defaultProvider = APIUtil.getPropsValue("defaultAuthProvider").getOrElse("")
  
  val userNotFoundError = "user (\\S+) at provider (\\S+) not found".r
  
  /**
   * The request vars ensure that for one page load, the same API call isn't
   * made multiple times
   */
  object allBanksVar extends RequestVar[Box[BanksJson]] (Empty)
  
  def allBanks : Box[BanksJson]= {
    allBanksVar.get match {
      case Full(a) => Full(a)
      case _ => ObpGet("/v1.2.1/banks").flatMap(_.extractOpt[BanksJson])
    }
  }
  
  trait SortDirection {
    val value : String
  }
  object ASC extends SortDirection { val value = "ASC" }
  object DESC extends SortDirection { val value = "DESC" }
  
  /**
   * @return Json for transactions of a particular bank account
   */
  def transactions(bankId: String, accountId: String, viewId: String, limit: Option[Int],
      offset: Option[Int], fromDate: Option[Date], toDate: Option[Date], sortDirection: Option[SortDirection]) : Box[TransactionsJson]= {
    
    val headers : List[Header] = limit.map(l => Header("obp_limit", l.toString)).toList ::: offset.map(o => Header("obp_offset", o.toString)).toList :::
      fromDate.map(f => Header("obp_from_date", dateFormat.format(f))).toList ::: toDate.map(t => Header("obp_to_date", dateFormat.format(t))).toList :::
      sortDirection.map(s => Header("obp_sort_direction", s.value)).toList ::: Nil
    
    ObpGet("/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) +
              "/transactions", headers).flatMap(x => x.extractOpt[TransactionsJson])
  }
  
  def publicAccounts(bankId : String) : Box[BarebonesAccountsJson] = {
    ObpGet("/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/public").flatMap(_.extractOpt[BarebonesAccountsJson])
  }

  def publicAccounts : Box[BarebonesAccountsJson] = {
    ObpGet("/v1.2.1/accounts/public").flatMap(_.extractOpt[BarebonesAccountsJson])
  }

  def privateAccounts(bankId : String) : Box[BarebonesAccountsJson] = {
    ObpGet("/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/private").flatMap(_.extractOpt[BarebonesAccountsJson])
  }

  def privateAccounts : Box[BarebonesAccountsJson] = {
    ObpGet("/v1.2.1/accounts/private").flatMap(_.extractOpt[BarebonesAccountsJson])
  }

  def account(bankId: String, accountId: String, viewId: String) : Box[AccountJson] = {
    ObpGet("/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + "/account").flatMap(x => x.extractOpt[AccountJson])
  }

  /**
   * @return True if the account was deleted
   */
  def deleteAccount(bankId : String, accountId : String) : Boolean  = {
    val deleteAccountUrl = "/internal/v1.0/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId)
    ObpInternalDelete(deleteAccountUrl)
  }
  
   /**
   * @return The json for the comment if it was successfully added
   */
  def addComment(bankId : String, accountId : String, viewId : String,
      transactionId: String, comment: String) : Box[TransactionCommentJson] = {
    
    val addCommentJson = ("value" -> comment)
    
    val addCommentUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) +
      "/transactions/" + urlEncode(transactionId) + "/metadata/comments"
    
    ObpPost(addCommentUrl, addCommentJson).flatMap(_.extractOpt[TransactionCommentJson])
  }
  
  def addPermission(bankId: String, accountId: String, userId : String, viewId: String) = {
    val grantPermissionUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + 
      "/permissions/" + urlEncode(defaultProvider) + "/" +urlEncode(userId) + "/views/" + urlEncode(viewId)
    ObpPost(grantPermissionUrl, new JObject(Nil))
  }
  
  def addPermissions(bankId: String, accountId: String, userId: String, viewIds : List[String]) : Box[JValue] = {
    val addPermissionsUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + 
         urlEncode(accountId) + "/permissions/" + urlEncode(defaultProvider) + "/" + urlEncode(userId) + "/views"
    val json = ("views" -> viewIds)
    
    for {
      result <- ObpPost(addPermissionsUrl, json)
    } yield result
  }
  
  def getPermissions(bankId: String, accountId : String) : Box[PermissionsJson] = {
    ObpGet("/v1.2.1/banks/" + bankId + "/accounts/" + accountId + "/permissions").flatMap(x => x.extractOpt[PermissionsJson])
  }

  def getViews(bankId: String, accountId: String) : Box[List[ViewJson]] = {
    for {
      json <- ObpGet("/v1.2.1/banks/" + bankId + "/accounts/" + accountId + "/views")
      viewsJson <- Box(json.extractOpt[ViewsJson])
    } yield viewsJson.views.getOrElse(Nil)
  }

  def getCompleteViews(bankId: String, accountId: String) : Box[List[CompleteViewJson]] = {
    for {
      json <- ObpGet("/v1.2.1/banks/" + bankId + "/accounts/" + accountId + "/views")
    } yield {
      json \ "views" match {
        case JArray(l) => l.map(viewJson => 
          viewJson.values match{
            case vals: Map[_, _] => CompleteViewJson(vals.asInstanceOf[Map[String, Any]])
            case _ => CompleteViewJson(Map.empty)
          })
        case _ => Nil
      }
    }
  }
  
  def removePermission(bankId: String, accountId: String, userId : String, viewId: String) = {
    val removePermissionUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/permissions/" +
      urlEncode(defaultProvider) + "/" + urlEncode(userId) + "/views/" + urlEncode(viewId)
    ObpDelete(removePermissionUrl)
  }
  
  def removeAllPermissions(bankId: String, accountId: String, userId: String) = {
    val removeAllPermissionsUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/permissions/" + 
      urlEncode(defaultProvider) + "/" + urlEncode(userId) + "/views"
    ObpDelete(removeAllPermissionsUrl)
  }
  
  /**
   * @return The jsons for the tags that were were successfully added
   */
  def addTags(bankId : String, accountId : String, viewId : String,
      transactionId: String, tags: List[String]) : List[TransactionTagJson] = {
    
    val addTagJsons = tags.map(tag => {
      ("value" -> tag)
    })
    
    val addTagUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + "/transactions/" + urlEncode(transactionId) + "/metadata/tags"
    
    addTagJsons.map(addTagJson => ObpPost(addTagUrl, addTagJson).flatMap(_.extractOpt[TransactionTagJson])).flatten
  }
  
  /**
   * @return True if the tag was deleted
   */
  def deleteTag(bankId : String, accountId : String, viewId : String,
      transactionId: String, tagId: String) : Boolean  = {
    val deleteTagUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + "/transactions/" + 
      urlEncode(transactionId) + "/metadata/tags/" + urlEncode(tagId)
    ObpDelete(deleteTagUrl)
  }
  
  /**
   * @return The json for the image if it was successfully added
   */
  def addImage(bankId : String, accountId : String, viewId : String,
      transactionId: String, imageURL: String, imageDescription: String) = {

    val json = 
      ("label" -> imageDescription) ~
      ("URL" -> imageURL)
    
    val addImageUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + 
      "/transactions/" + urlEncode(transactionId) + "/metadata/images"
      
    ObpPost(addImageUrl, json).flatMap(_.extractOpt[TransactionImageJson])
  }
  
  /**
   * @return True if the image was deleted
   */
  def deleteImage(bankId : String, accountId : String, viewId : String,
      transactionId: String, imageId: String) : Boolean  = {
    
    val deleteImageUrl = "/v1.2.1/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + 
      "/transactions/" + urlEncode(transactionId) + "/metadata/images/" + urlEncode(imageId)
    ObpDelete(deleteImageUrl)
  }


  def updateView(bankId: String, accountId: String, viewId: String, viewUpdateJson : JValue) : Box[Unit] = {
    for {
      response <- ObpPut("/v1.2.1/banks/" + bankId + "/accounts/" + accountId + "/views/" + viewId, viewUpdateJson)
    } yield Unit
  }
}



object OBPRequest extends MdcLoggable {
  implicit val formats = CustomJsonFormats.formats
  //returns a tuple of the status code and response body as a string
  def apply(apiPath : String, jsonBody : Option[JValue], method : String, headers : List[Header]) : Box[(Int, String)] = {
    val statusAndBody = tryo {
      val credentials = OAuthClient.getAuthorizedCredential
      val apiUrl = OAuthClient.currentApiBaseUrl
      val url = new URL(apiUrl + apiPath)
      //bleh
      val request = url.openConnection().asInstanceOf[HttpURLConnection] //blagh!
      request.setDoOutput(true)
      request.setRequestMethod(method)
      request.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      request.setRequestProperty("Accept", "application/json")
      request.setRequestProperty("Accept-Charset", "UTF-8")

      headers.foreach(header => request.setRequestProperty(header.key, header.value))

      //sign the request if we have some credentials to sign it with
      credentials.foreach(c => c.consumer.sign(request))

      //Set the request body
      if(jsonBody.isDefined) {
        val output = request.getOutputStream()
        val writer = new BufferedWriter(new OutputStreamWriter(output, "UTF-8"))
        writer.write(compactRender(jsonBody.get)).toString
        writer.flush()
        writer.close()
      }

      request.connect()
      val status = request.getResponseCode()

      //get reponse body
      val inputStream = if(status >= 400) request.getErrorStream() else request.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))
      val builder = new StringBuilder()
      var line = ""
      def readLines() {
        line = reader.readLine()
        if (line != null) {
          builder.append(line + "\n")
          readLines()
        }
      }
      readLines()
      reader.close();
      (status, builder.toString())
    }

    statusAndBody pass {
      case Failure(msg, ex, _) => {
        val sw = new StringWriter()
        val writer = new PrintWriter(sw)
        ex.foreach(_.printStackTrace(writer))
        logger.debug("Error making api call: " + msg + ", stack trace: " + sw.toString)
      }
      case _ => Unit
    }
  }
}

//Ugly duplicate of above to be able to get rid of /obp prefix.
//Should be done without it
object OBPInternalRequest extends MdcLoggable {
  implicit val formats = CustomJsonFormats.formats
  //returns a tuple of the status code and response body as a string
  def apply(apiPath : String, jsonBody : Option[JValue], method : String, headers : List[Header]) : Box[(Int, String)] = {
    val statusAndBody = tryo {
      val credentials = OAuthClient.getAuthorizedCredential
      val apiUrl = OBPDemo.baseUrl
      val url = new URL(apiUrl + apiPath)
      //bleh
      val request = url.openConnection().asInstanceOf[HttpURLConnection] //blagh!
      request.setDoOutput(true)
      request.setRequestMethod(method)
      request.setRequestProperty("Content-Type", "application/json")
      request.setRequestProperty("Accept", "application/json")

      headers.foreach(header => request.setRequestProperty(header.key, header.value))

      //sign the request if we have some credentials to sign it with
      credentials.foreach(c => c.consumer.sign(request))

      //Set the request body
      if(jsonBody.isDefined) {
        val output = request.getOutputStream()
        val body = compactRender(jsonBody.get).getBytes()
        output.write(body)
        output.flush()
        output.close()
      }
      request.connect()

      val status = request.getResponseCode()

      //bleh
      val inputStream = if(status >= 400) request.getErrorStream() else request.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      val builder = new StringBuilder()
      var line = ""
      def readLines() {
        line = reader.readLine()
        if (line != null) {
          builder.append(line + "\n")
          readLines()
        }
      }
      readLines()
      reader.close();
      (status, builder.toString())
    }

    statusAndBody pass {
      case Failure(msg, ex, _) => {
        val sw = new StringWriter()
        val writer = new PrintWriter(sw)
        ex.foreach(_.printStackTrace(writer))
        logger.debug("Error making api call: " + msg + ", stack trace: " + sw.toString)
      }
      case _ => Unit
    }
  }
}

object ObpPut {
  def apply(apiPath: String, json : JValue): Box[JValue] = {
    OBPRequest(apiPath, Some(json), "PUT", Nil).flatMap {
      case(status, result) => APIUtils.getAPIResponseBody(status, result)
    }
  }
}

object ObpPost {
  def apply(apiPath: String, json : JValue): Box[JValue] = {
    OBPRequest(apiPath, Some(json), "POST", Nil).flatMap {
      case(status, result) => APIUtils.getAPIResponseBody(status, result)
    }
  }
}

object ObpDelete {
  /**
   * @return True if the delete worked
   */
  def apply(apiPath: String): Boolean = {
    val worked = OBPRequest(apiPath, None, "DELETE", Nil).map {
      case(status, result) => APIUtils.apiResponseWorked(status, result)
    }
    worked.getOrElse(false)
  }
}

object ObpGet {
  def apply(apiPath: String, headers : List[Header] = Nil): Box[JValue] = {
    OBPRequest(apiPath, None, "GET", headers).flatMap {
      case(status, result) => APIUtils.getAPIResponseBody(status, result)
    }
  }
}

object ObpInternalDelete {
  /**
   * @return True if the delete worked
   */
  def apply(apiPath: String): Boolean = {
    val worked = OBPInternalRequest(apiPath, None, "DELETE", Nil).map {
      case(status, result) => APIUtils.apiResponseWorked(status, result)
    }
    worked.getOrElse(false)
  }
}

object APIUtils extends MdcLoggable {
  implicit val formats = CustomJsonFormats.formats

  def getAPIResponseBody(responseCode : Int, body : String) : Box[JValue] = {
    responseCode match {
      case 200 | 201 => tryo{parse(body)}
      case _ => {
        val failMsg = "Bad response code (" + responseCode + ") from OBP API server: " + body
        logger.warn(failMsg)
        Failure(failMsg)
      }
    }
  }

  def apiResponseWorked(responseCode : Int, result : String) : Boolean = {
    responseCode match {
      case 200 | 201 | 204 => true
      case _ => false
    }
  }
}

object ObpJson {
  implicit val formats = CustomJsonFormats.formats
  case class BanksJson(banks : Option[List[BankJson]]) {
    def bankJsons: List[BankJson] = {
      banks.toList.flatten
    }
  }
  case class BankJson(id: Option[String], 
    short_name: Option[String],
    full_name: Option[String],
    logo: Option[String],
    website: Option[String])
		  		  
  case class UserJson(id: Option[String],
    provider: Option[String],
    display_name: Option[String])

  case class AccountBalanceJson(currency: Option[String],
    amount: Option[String])		  	
	
    //simplified version of what we actually get back from the api
  case class ViewJson(
    id: Option[String],
    short_name: Option[String],
    description: Option[String],
    is_public: Option[Boolean])
            
  case class ViewsJson(views: Option[List[ViewJson]])

  case class CompleteViewJson(json: Map[String, Any]){
    val id: Option[String] = json.get("id") match {
      case Some(s : String) => Some(s)
      case _ => None
    }

    val shortName: Option[String] = json.get("short_name") match {
      case Some(s : String) => Some(s)
      case _ => None
    }

    val alias: Option[String] = json.get("alias") match {
      case Some(s : String) => Some(s)
      case _ => None
    }

    val description: Option[String] = json.get("description") match {
      case Some(s : String) => Some(s)
      case _ => None
    }

    val isPublic: Option[Boolean] = json.get("is_public") match {
      case Some(b : Boolean) => Some(b)
      case _ => None
    }

    val booleans = json.collect{ case (s: String, b: Boolean) => (s,b)}

    val permissions = booleans.filterNot(_.key == "is_public")
  }


  case class AccountJson(id: Option[String],
    label: Option[String],
    number: Option[String],
    owners: Option[List[UserJson]],
    `type`: Option[String],
    balance: Option[AccountBalanceJson],
    IBAN : Option[String],
    views_available: Option[List[ViewJson]])
		  			 
  case class BarebonesAccountsJson(accounts: Option[List[BarebonesAccountJson]])
  
  case class BarebonesAccountJson(id: Option[String],
    label: Option[String],
    views_available: Option[List[ViewJson]],
    bank_id: Option[String])
		  						  
  case class HolderJson(name: Option[String],
		is_alias : Option[Boolean])
		  				
  //TODO: Can this go with BankJson?
  case class LightBankJson(national_identifier: Option[String],
    name: Option[String])
  
  case class ThisAccountJson(holders: Option[List[HolderJson]],
    number: Option[String],
    kind: Option[String],
    IBAN: Option[String],
    bank: Option[LightBankJson])
  
  case class LocationJson(latitude: Option[Double],
    longitude: Option[Double],
    date: Option[Date], //TODO: Check if the default date formatter is okay
    user: Option[UserJson])

  case class OtherAccountMetadataJson(
    public_alias: Option[String],
    private_alias: Option[String],
    more_info: Option[String],
    URL: Option[String],
    image_URL: Option[String],
    open_corporates_URL: Option[String],
    corporate_location: Option[LocationJson],
    physical_location: Option[LocationJson])		  					 

  //TODO: Why can't an other account have more than one holder?	  					 
  case class OtherAccountJson(
    id: Option[String],
    holder: Option[HolderJson],
    number: Option[String],
    kind: Option[String],
    IBAN: Option[String],
    bank: Option[LightBankJson],
    metadata: Option[OtherAccountMetadataJson])

  case class OtherAccountsJson(other_accounts: Option[List[OtherAccountJson]])
  
  case class TransactionValueJson(currency: Option[String],
    amount: Option[String])
		  					  
  case class TransactionDetailsJson(`type`: Option[String],
    label: Option[String],
    posted: Option[Date], //TODO: Check if the default date formatter is okay
    completed: Option[Date], //TODO: Check if the default date formatter is okay
    new_balance: Option[AccountBalanceJson],
    value: Option[TransactionValueJson])	  					  
		  					  
  case class TransactionCommentJson(id: Option[String],
    date: Option[Date], //TODO: Check if the default date formatter is okay
    value: Option[String],
    user: Option[UserJson],
    reply_to: Option[String])
  
  case class TransactionTagJson(id: Option[String],
    date: Option[Date], //TODO: Check if the default date formatter is okay
    value: Option[String],
    user: Option[UserJson])
  
  case class TransactionImageJson(id: Option[String],
    label: Option[String],
    date: Option[Date], //TODO: Check if the default date formatter is okay
    URL: Option[String],
    user: Option[UserJson])
  
  case class TransactionMetadataJson(narrative: Option[String],
    comments: Option[List[TransactionCommentJson]],
    tags: Option[List[TransactionTagJson]],
    images: Option[List[TransactionImageJson]],
    where: Option[LocationJson])
  
  case class TransactionJson(uuid: Option[String],
    id: Option[String],
    this_account: Option[ThisAccountJson],
    other_account: Option[OtherAccountJson],
    details: Option[TransactionDetailsJson],
    metadata: Option[TransactionMetadataJson]) {
    
    lazy val imageJsons : Option[List[TransactionImageJson]] = {
      metadata.flatMap(_.images)
    }
    
    lazy val tagJsons : Option[List[TransactionTagJson]] = {
      metadata.flatMap(_.tags)
    }
    
    lazy val commentJsons : Option[List[TransactionCommentJson]] = {
      metadata.flatMap(_.comments)
    }
  }
  
  case class TransactionsJson(transactions: Option[List[TransactionJson]])
  
  case class PermissionJson(user: Option[UserJson], views: Option[List[ViewJson]])
  
  case class PermissionsJson(permissions : Option[List[PermissionJson]])
  
}