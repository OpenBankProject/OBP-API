package code.util

import java.net.{Socket, SocketException}
import java.util.UUID.randomUUID
import java.util.{Date, GregorianCalendar}

import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import net.liftweb.common._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST._
import net.liftweb.json.{DateFormat, Formats}
import org.apache.commons.lang3.StringUtils
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{RequiredFieldValidation, RequiredInfo}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.http.S
import net.liftweb.util.Helpers

import scala.concurrent.Future
import scala.util.Random
import scala.reflect.runtime.universe.Type
import scala.concurrent.duration._



object Helper{

  /**
    *
    *
    */

  // If we need to return a string and all good, return an empty string
  // rule of silence http://www.linfo.org/rule_of_silence.html
  val SILENCE_IS_GOLDEN = ""


  /**
   * A css selector that will (unless you have a template containing an element
   * name i_am_an_id_that_should_never_exist) have no effect. Useful when you have
   * a method that needs to return a CssSel but in some code paths don't want to do anything.
   */
  val NOOP_SELECTOR = {
    import net.liftweb.util.Helpers._
    "#i_am_an_id_that_should_never_exist" #> ""
  }

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

  /**
    * Helper function which wrap some statement into Future.
    * The function is curried i.e.
    * use this parameter syntax ---> (failMsg: String)(statement: => Boolean)
    * instead of this one ---------> (failMsg: String, statement: => Boolean)
    * Below is an example of recommended usage.
    * Please note that the second parameter is provided in curly bracket in order to mimics body of a function.
    *   booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
    *     hasEntitlement("", u.userId, ApiRole.CanGetAnyUser)
    *   }
    * @param failMsg is used in case that result of call of function booleanToBox returns Empty
    * @param statement is call by name parameter.
    * @return In case the statement is false the function returns Future[Failure(failMsg)].
    *         Otherwise returns Future[Full()].
    */
  def booleanToFuture(failMsg: String, failCode: Int = 400)(statement: => Boolean): Future[Box[Unit]] = {
    Future{
      booleanToBox(statement)
    } map {
      x => fullBoxOrException(x ~> APIFailureNewStyle(failMsg, failCode))
    }
  }
  /**
    * Helper function which wrap some statement into Future.
    * The function is curried i.e.
    * use this parameter syntax ---> (failMsg: String)(statement: => Boolean)
    * instead of this one ---------> (failMsg: String, statement: => Boolean)
    * Below is an example of recommended usage.
    * Please note that the second parameter is provided in curly bracket in order to mimics body of a function.
    *   booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
    *     hasEntitlement("", u.userId, ApiRole.CanGetAnyUser)
    *   }
    * @param failMsg is used in case that result of call of function booleanToBox returns Empty
    * @param statement is call by name parameter.
    * @return In case the statement is false the function returns Future[Failure(failMsg)].
    *         Otherwise returns Future[Full()].
    */
  def wrapStatementToFuture(failMsg: String, failCode: Int = 400)(statement: => Boolean): Future[Box[Boolean]] = {
    Future {
      statement match {
        case true => Full(statement)
        case false => Empty
      }
    } map {
      x => fullBoxOrException(x ~> APIFailureNewStyle(failMsg, failCode))
    }
  }

  val deprecatedJsonGenerationMessage = "json generation handled elsewhere as it changes from api version to api version"

  /**
   * Converts a number representing the smallest unit of a currency into a big decimal formatted according to the rules of
   * that currency. E.g. JPY: 1000 units (yen) => 1000, EUR: 1000 units (cents) => 10.00
   */
  def smallestCurrencyUnitToBigDecimal(units : Long, currencyCode : String) = {
    BigDecimal(units, currencyDecimalPlaces(currencyCode))
  }

  /**
   * Returns the number of decimal places a currency has. E.g. "EUR" -> 2, "JPY" -> 0
    *
    * @param currencyCode
   * @return
   */
  def currencyDecimalPlaces(currencyCode : String) = {
    //this data was sourced from Wikipedia, so it might not all be correct,
    //and some banking systems may still retain different units (e.g. CZK?)
    //notable it doesn't cover non-traditional currencies (e.g. cryptocurrencies)
    currencyCode match {
      //TODO: handle MRO and MGA, which are non-decimal
      case "CZK" | "JPY" | "KRW" => 0
      case "KWD" | "OMR" => 3
      case _ => 2
    }
  }

  /**
   * E.g.
   * amount: BigDecimal("12.45"), currencyCode : "EUR" => 1245
   * amount: BigDecimal("9034"), currencyCode : "JPY" => 9034
   */
  def convertToSmallestCurrencyUnits(amount : BigDecimal, currencyCode : String) : Long = {
    val decimalPlaces = Helper.currencyDecimalPlaces(currencyCode)

    (amount * BigDecimal("10").pow(decimalPlaces)).toLong
  }


  /*
  Returns a pretty json representation of the input
   */
  def prettyJson(input: JValue) : String = {
    implicit val formats = CustomJsonFormats.formats
    prettyRender(decompose(input))
  }

  /**
    * extract clean redirect url from input value, because input may have some parameters, such as the following examples  <br/> 
    * eg1: http://localhost:8082/oauthcallback?....--> http://localhost:8082 <br/> 
    * eg2: http://localhost:8016?oautallback?=3NLMGV ...--> http://localhost:8016
    *
    * @param input a long url with parameters 
    * @return clean redirect url
    */
  def extractCleanRedirectURL(input: String): Box[String] = {
    /**
      * pattern eg1: http://xxxxxx?oautxxxx  -->http://xxxxxx
      * pattern eg2: https://xxxxxx/oautxxxx -->http://xxxxxx
      */
    //Note: the pattern should be : val  pattern = "(https?):\\/\\/(.*)(?=((\\/)|(\\?))oauthcallback*)".r, but the OAuthTest is different, so add the following logic
    val pattern = "([A-Za-z][A-Za-z0-9+.-]*):\\/\\/(.*)(?=((\\/)|(\\?))oauth*)".r
    val validRedirectURL = pattern findFirstIn input
    // Now for the OAuthTest, the redirect format is : http://localhost:8016?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018
    // It is not the normal case: http://localhost:8082/oauthcallback?oauth_token=LUDKELGJXRDOC1AK1X1TOYIXM5W1AORFJT5KE43B&oauth_verifier=14062
    // So add the split function to select the first value; eg: Array(http://localhost:8082, thcallback) --> http://localhost:8082
    val extractCleanURL = validRedirectURL.getOrElse("").split("/oauth")(0) 
    Full(extractCleanURL)
  }
  
  /**
    * extract Oauth Token String from input value, because input may have some parameters, such as the following examples  <br/> 
    * http://localhost:8082/oauthcallback?oauth_token=DKR242MB3IRCUVG35UZ0QQOK3MBS1G2HL2ZIKK2O&oauth_verifier=64465 
    *   -->  DKR242MB3IRCUVG35UZ0QQOK3MBS1G2HL2ZIKK2O
    * 
    * @param input a long url with parameters 
    * @return Oauth Token String
    */
  def extractOauthToken(input: String): Box[String] = {
    Full(input.split("oauth_token=")(1).split("&")(0))
  }

  /**
    * check the redirect url is valid with default values.
    */
  def isValidInternalRedirectUrl(url: String) : Boolean = {
    //set the default value is "/" and "/oauth/authorize"
    val validUrls = List("/","/oauth/authorize","/consumer-registration","/dummy-user-tokens","/create-sandbox-account", "/otp")

    //case1: OBP-API login: url = "/"
    //case2: API-Explore oauth login: url = "/oauth/authorize?oauth_token=V0JTCDYXWUNTXDZ3VUDNM1HE3Q1PZR2WJ4PURXQA&logUserOut=false"
    val extractCleanURL = StringUtils.substringBefore(url, "?")

    validUrls.contains(extractCleanURL)
  }

   /**
    * Used for version extraction from props string
    */
  val matchKafkaVersion = "kafka_v([0-9a-zA-Z_]+)".r
  val matchAnyKafka = "^kafka.*$".r
  
  /**
    * change the TimeZone to the current TimeZOne
    * reference the following trait
    * net.liftweb.json
    * trait DefaultFormats
    * extends Formats
    */
    //TODO need clean this format, we have set the TimeZone in boot.scala
  val DateFormatWithCurrentTimeZone = new Formats {
  
    import java.text.{ParseException, SimpleDateFormat}
  
    val dateFormat = new DateFormat {
      def parse(s: String) = try {
        Some(formatter.parse(s))
      } catch {
        case e: ParseException => None
      }
    
      def format(d: Date) = formatter.format(d)
    
      private def formatter = {
        val f = dateFormatter
        f.setTimeZone(new GregorianCalendar().getTimeZone)
        f
      }
    }
  
    protected def dateFormatter = APIUtil.DateWithMsFormat
  }

  def getHostname(): String = {
    APIUtil.getPropsValue("hostname", "") match {
      case s: String if s.nonEmpty => s.split(":").lift(1) match {
        case Some(s) => s.replaceAll("\\/", "").replaceAll("\\.", "-")
        case None => "unknown"
      }
      case _ => "unknown"
    }
  }

  def getRemotedataHostname(): String = {
    APIUtil.getPropsValue("remotedata.hostname", "") match {
      case s: String if s.nonEmpty => s.replaceAll("\\/", "").replaceAll("\\.", "-")
      case _ => "unknown"
    }
  }
  
  def getAkkaConnectorHostname(): String = {
    APIUtil.getPropsValue("akka_connector.hostname", "") match {
      case s: String if s.nonEmpty => s.replaceAll("\\/", "").replaceAll("\\.", "-")
      case _ => "unknown"
    }
  }

  def findAvailablePort(): Int = {
    val PORT_RANGE_MIN = 2552
    val PORT_RANGE_MAX = 2661
    val random = new Random(System.currentTimeMillis())

    def findRandomPort() = {
			val portRange = PORT_RANGE_MAX - PORT_RANGE_MIN
			PORT_RANGE_MIN + random.nextInt(portRange + 1)
		}

    def isPortAvailable(port: Int): Boolean = {
      var result = true
      try {
        new Socket("localhost", port).close()
        result = false
      }
      catch {
        case e: SocketException =>
      }
      result
    }

    var candidatePort = -1
    do {
      candidatePort = findRandomPort()
    }
    while (!isPortAvailable(candidatePort))
    candidatePort
  }

  trait MdcLoggable extends Loggable {
    protected def initiate(): Unit = ()

    initiate()
    MDC.put("host" -> getHostname)
  }

  /*
  Return true for Y, YES and true etc.
   */
  def stringToBooleanOption(input : String) : Option[Boolean] = {
    var upperInput = input.toUpperCase()
    upperInput match {
      case "Y" | "YES" | "TRUE" | "1" | "-1" => Full(true)
      case "N" | "NO" | "FALSE" | "0" => Full(false)
      case _ => None
    }
  }

  /*
  Return "Y" for true, "N" for false and "" if None
   */
  def optionBooleanToString(input : Option[Boolean]) : String = {
    val result : String = input match {
      case Some(a) => a match {
        case true => "Y"
        case false => "N"
      }
      case _ => ""
    }
    result
  }

  /**
   * get given type Required Field Info, cache the result
   * @param tpe
   * @return RequiredInfo
   */
  def getRequiredFieldInfo(tpe: Type): RequiredInfo = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      code.api.cache.Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (100000 days) {

        RequiredFieldValidation.getRequiredInfo(tpe)

      }
    }
  }

  def i18n(message: String): String = {
    if(S.?(message)==message) {
      val words = message.split('.').toList match {
        case x :: Nil => Helpers.capify(x) :: Nil
        case x :: xs  => Helpers.capify(x) :: xs
        case _        => Nil
      }
      words.mkString(" ") + "."
    }
    else S.?(message)
  }

}