package code.util

import code.api.cache.{Redis, RedisLogger}

import java.net.{Socket, SocketException, URL}
import java.util.UUID.randomUUID
import java.util.{Date, GregorianCalendar}
import code.api.util.{APIUtil, CallContext, CallContextLight, CustomJsonFormats}
import code.api.{APIFailureNewStyle, Constant}
import code.api.util.APIUtil.fullBoxOrException
import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.transaction.internalMapping.MappedTransactionIdMappingProvider
import net.liftweb.common._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST._
import net.liftweb.json.{DateFormat, Formats}
import org.apache.commons.lang3.StringUtils
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountBalance, AccountBalances, AccountHeld, AccountId, CoreAccount, Customer, CustomerId, Transaction, TransactionCore, TransactionId}
import com.openbankproject.commons.util.{ReflectUtils, RequiredFieldValidation, RequiredInfo}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.http.S
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}

import java.lang.reflect.Method
import java.text.SimpleDateFormat
import scala.concurrent.Future
import scala.util.Random
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe._
import scala.concurrent.duration._



object Helper extends Loggable {

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
  def booleanToFuture(failMsg: String, failCode: Int = 400, cc: Option[CallContext])(statement: => Boolean): Future[Box[Unit]] = {
    Future{
      booleanToBox(statement)
    } map {
      x => fullBoxOrException(x ~> APIFailureNewStyle(failMsg, failCode, cc.map(_.toLight)))
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
   *
   * @param redirectUrl eg: http://localhost:8082/oauthcallback?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018
   * @return http://localhost:8082/oauthcallback
   */
  def getStaticPortionOfRedirectURL(redirectUrl: String): Box[String] = {
    tryo(redirectUrl.split("\\?")(0)) //return everything before the "?"
  }

  /**
   * extract clean redirect url from input value, because input may have some parameters, such as the following examples  <br/>
   * eg1: http://localhost:8082/oauthcallback?....--> http://localhost:8082 <br/>
   * eg2: http://localhost:8016?oautallback?=3NLMGV ...--> http://localhost:8016
   *
   * @param redirectUrl -> http://localhost:8082/oauthcallback?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018
   * @return hostOnlyOfRedirectURL-> http://localhost:8082
   */
  @deprecated("We can not only use hostname as the redirectUrl, now add new method `getStaticPortionOfRedirectURL` ","05.12.2023")
  def getHostOnlyOfRedirectURL(redirectUrl: String): Box[String] = {
    val url = new URL(redirectUrl) //eg: http://localhost:8082/oauthcallback?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018
    val protocol = url.getProtocol() // http
    val authority = url.getAuthority()// localhost:8082, this will contain the port.
    tryo(s"$protocol://$authority") // http://localhost:8082
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
    val internalRedirectUrlsWhiteList = List(
      "/","/oauth/authorize","/consumer-registration",
      "/dummy-user-tokens","/create-sandbox-account",
      "/add-user-auth-context-update-request","/otp",
      "/terms-and-conditions", "/privacy-policy",
      "/confirm-bg-consent-request",
      "/confirm-bg-consent-request-sca",
      "/confirm-vrp-consent-request",
      "/confirm-vrp-consent",
      "/consent-screen",
      "/consent",
    )

    //case1: OBP-API login: url = "/"
    //case2: API-Explore oauth login: url = "/oauth/authorize?oauth_token=V0JTCDYXWUNTXDZ3VUDNM1HE3Q1PZR2WJ4PURXQA&logUserOut=false"
    val extractCleanURL = StringUtils.substringBefore(url, "?")

    internalRedirectUrlsWhiteList.contains(extractCleanURL)
  }

   /**
    * Used for version extraction from props string
    */
  val matchAnyStoredProcedure = "stored_procedure.*|star".r

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
    Constant.HostName match {
      case s: String if s.nonEmpty => s.split(":").lift(1) match {
        case Some(s) => s.replaceAll("\\/", "").replaceAll("\\.", "-")
        case None => "unknown"
      }
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

    // Capture the class name of the component mixing in this trait
    private val clazzName: String = this.getClass.getSimpleName.replaceAll("\\$", "")

    override protected val logger: net.liftweb.common.Logger = {
      val loggerName = this.getClass.getName

      new net.liftweb.common.Logger {

        private val underlyingLogger = net.liftweb.common.Logger(loggerName)

        private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX")
        dateFormat.setTimeZone(java.util.TimeZone.getDefault) // force local TZ

        private def toRedisFormat(msg: AnyRef): String = {
          val ts = dateFormat.format(new Date())
          val thread = Thread.currentThread().getName
          s"[$ts] [$thread] [$clazzName] ${msg.toString}"
        }

      // INFO
      override def info(msg: => AnyRef): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.info(maskedMsg)
        RedisLogger.logAsync(RedisLogger.LogLevel.INFO, toRedisFormat(maskedMsg))
      }

      override def info(msg: => AnyRef, t: => Throwable): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.info(maskedMsg, t)
        RedisLogger.logAsync(RedisLogger.LogLevel.INFO, toRedisFormat(maskedMsg) + "\n" + t.toString)
      }

      // WARN
      override def warn(msg: => AnyRef): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.warn(maskedMsg)
        RedisLogger.logAsync(RedisLogger.LogLevel.WARNING, toRedisFormat(maskedMsg))
      }

      override def warn(msg: => AnyRef, t: Throwable): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.warn(maskedMsg, t)
        RedisLogger.logAsync(RedisLogger.LogLevel.WARNING, toRedisFormat(maskedMsg) + "\n" + t.toString)
      }

      // ERROR
      override def error(msg: => AnyRef): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.error(maskedMsg)
        RedisLogger.logAsync(RedisLogger.LogLevel.ERROR, toRedisFormat(maskedMsg))
      }

      override def error(msg: => AnyRef, t: Throwable): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.error(maskedMsg, t)
        RedisLogger.logAsync(RedisLogger.LogLevel.ERROR, toRedisFormat(maskedMsg) + "\n" + t.toString)
      }

      // DEBUG
      override def debug(msg: => AnyRef): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.debug(maskedMsg)
        RedisLogger.logAsync(RedisLogger.LogLevel.DEBUG, toRedisFormat(maskedMsg))
      }

      override def debug(msg: => AnyRef, t: Throwable): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.debug(maskedMsg, t)
        RedisLogger.logAsync(RedisLogger.LogLevel.DEBUG, toRedisFormat(maskedMsg) + "\n" + t.toString)
      }

      // TRACE
      override def trace(msg: => AnyRef): Unit = {
        val maskedMsg = SecureLogging.maskSensitive(msg)
        underlyingLogger.trace(maskedMsg)
        RedisLogger.logAsync(RedisLogger.LogLevel.TRACE, toRedisFormat(maskedMsg))
      }

      // Delegate enabled checks
      override def isDebugEnabled: Boolean = underlyingLogger.isDebugEnabled
      override def isErrorEnabled: Boolean = underlyingLogger.isErrorEnabled
      override def isInfoEnabled: Boolean = underlyingLogger.isInfoEnabled
      override def isTraceEnabled: Boolean = underlyingLogger.isTraceEnabled
      override def isWarnEnabled: Boolean = underlyingLogger.isWarnEnabled
    }
  }

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
      code.api.cache.Caching.memoizeSyncWithImMemory (Some(cacheKey.toString())) (100000 days) {

        RequiredFieldValidation.getRequiredInfo(tpe)

      }
    }
  }

  def i18n(message: String, default: Option[String] = None): String = {
    if (S.inStatefulScope_?) {
      if (S.?(message) == message) {
        val words = message.split('.').toList match {
          case x :: Nil => Helpers.capify(x) :: Nil
          case x :: xs => Helpers.capify(x) :: xs
          case _ => Nil
        }
        default.getOrElse(words.mkString(" ") + ".")
      } else
        S.?(message)
    } else {
      logger.error(s"i18n(message($message), default${default}: Attempted to use resource bundles outside of an initialized S scope. " +
        s"S only usable when initialized, such as during request processing. Did you call S.? from Future?")
      default.getOrElse(message)
    }
  }

  /**
   * helper function to convert customerId and accountId in a given instance
   * @param obj
   * @param customerIdConverter customerId converter, to or from customerReference
   * @param accountIdConverter accountId converter, to or from accountReference
   * @tparam T type of instance
   * @return modified instance
   */
  private def convertId[T](
    obj: T,
    customerIdConverter: String=> String,
    accountIdConverter: String=> String,
    transactionIdConverter: String=> String
  ): T = {
    //1st: We must not convert when connector == mapped. this will ignore the implicitly_convert_ids props.
    //2rd: if connector != mapped, we still need the `implicitly_convert_ids == true`

    def isCustomerId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
      ownerType =:= typeOf[CustomerId] ||
        (fieldName.equalsIgnoreCase("customerId") && fieldType =:= typeOf[String]) ||
        (ownerType <:< typeOf[Customer] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
    }

    def isAccountId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
      ownerType <:< typeOf[AccountId] ||
        (fieldName.equalsIgnoreCase("accountId") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[CoreAccount] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountBalance] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountBalances] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountHeld] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
    }

    def isTransactionId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
      ownerType <:< typeOf[TransactionId] ||
        (fieldName.equalsIgnoreCase("transactionId") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[TransactionCore] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[Transaction] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
    }

    if(APIUtil.getPropsValue("connector","mapped") != "mapped" && APIUtil.getPropsAsBoolValue("implicitly_convert_ids",false)){
      ReflectUtils.resetNestedFields(obj){
        case (fieldName, fieldType, fieldValue: String, ownerType) if isCustomerId(fieldName, fieldType, fieldValue, ownerType) => customerIdConverter(fieldValue)
        case (fieldName, fieldType, fieldValue: String, ownerType) if isAccountId(fieldName, fieldType, fieldValue, ownerType) => accountIdConverter(fieldValue)
        case (fieldName, fieldType, fieldValue: String, ownerType) if isTransactionId(fieldName, fieldType, fieldValue, ownerType) => transactionIdConverter(fieldValue)
      }
      obj
    } else
      obj
  }

  /**
   * convert given instance nested CustomerId to customerReference, AccountId to accountReference
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToReference[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerId: String): String = MappedCustomerIdMappingProvider
      .getCustomerPlainTextReference(CustomerId(customerId))
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerId is $customerId")
    def accountIdConverter(accountId: String): String = MappedAccountIdMappingProvider
      .getAccountPlainTextReference(AccountId(accountId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid accountId is $accountId")
    def transactionIdConverter(transactionId: String): String = MappedTransactionIdMappingProvider
      .getTransactionPlainTextReference(TransactionId(transactionId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid transactionId is $transactionId")
    convertId[T](obj, customerIdConverter, accountIdConverter, transactionIdConverter)
  }

  /**
   * convert given instance nested customerReference to CustomerId, accountReference to AccountId
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToId[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerReference: String): String = MappedCustomerIdMappingProvider
      .getOrCreateCustomerId(customerReference)
      .map(_.value)
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerReference is $customerReference")
    def accountIdConverter(accountReference: String): String = MappedAccountIdMappingProvider
      .getOrCreateAccountId(accountReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid accountReference is $accountReference")
    def transactionIdConverter(transactionReference: String): String = MappedTransactionIdMappingProvider
      .getOrCreateTransactionId(transactionReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid transactionReference is $transactionReference")
    if(obj.isInstanceOf[EmptyBox]) {
      obj
    } else {
      convertId[T](obj, customerIdConverter, accountIdConverter, transactionIdConverter)
    }
  }

  lazy val ObpS: S = {
    val intercept: MethodInterceptor = (_: Any, method: Method, args: Array[AnyRef], _: MethodProxy) => {

      lazy val result = method.invoke(net.liftweb.http.S, args: _*)
      val methodName = method.getName

      if (methodName.equals("param")&&result.isInstanceOf[Box[String]]&&result.asInstanceOf[Box[String]].isDefined) {
        //we provide the basic check for all the parameters
        val resultAfterChecked =
          if((args.length>0) && args.apply(0).toString.equalsIgnoreCase("username")) {
            result.asInstanceOf[Box[String]].filter(APIUtil.checkUsernameString(_)==SILENCE_IS_GOLDEN)
          }else if((args.length>0) && args.apply(0).toString.equalsIgnoreCase("password")){
            result.asInstanceOf[Box[String]].filter(APIUtil.basicPasswordValidation(_)==SILENCE_IS_GOLDEN)
          }else if((args.length>0) && args.apply(0).toString.equalsIgnoreCase("consumer_key")){
            result.asInstanceOf[Box[String]].filter(APIUtil.basicConsumerKeyValidation(_)==SILENCE_IS_GOLDEN)
          }else if((args.length>0) && args.apply(0).toString.equalsIgnoreCase("redirectUrl")){
            result.asInstanceOf[Box[String]].filter(APIUtil.basicUriAndQueryStringValidation(_))
          } else{
            result.asInstanceOf[Box[String]].filter(APIUtil.checkMediumString(_)==SILENCE_IS_GOLDEN)
          }
        if(resultAfterChecked.isEmpty) {
          logger.debug(s"ObpS.${methodName} validation failed. (resultAfterChecked.isEmpty A) The input key is: ${if (args.length>0)args.apply(0) else ""}, value is:$result")
        }
        resultAfterChecked
      } else if (methodName.equals("uri") && result.isInstanceOf[String]){
        val resultAfterChecked = Full(result.asInstanceOf[String]).filter(APIUtil.basicUriAndQueryStringValidation(_))
        if(resultAfterChecked.isDefined) {
          resultAfterChecked.head
        }else{
          logger.debug(s"ObpS.${methodName} validation failed (NOT resultAfterChecked.isDefined). The value is:$result")
          resultAfterChecked.getOrElse("")
        }
      } else if (methodName.equals("uriAndQueryString") && result.isInstanceOf[Box[String]] && result.asInstanceOf[Box[String]].isDefined ||
        methodName.equals("queryString") && result.isInstanceOf[Box[String]]&&result.asInstanceOf[Box[String]].isDefined){
        val resultAfterChecked = result.asInstanceOf[Box[String]].filter(APIUtil.basicUriAndQueryStringValidation(_))
        if(resultAfterChecked.isEmpty) {
          logger.debug(s"ObpS.${methodName} validation failed. (resultAfterChecked.isEmpty B) The value is:$result")
        }
        resultAfterChecked
      } else {
        result
      }
    }

    val enhancer: Enhancer = new Enhancer()
    enhancer.setSuperclass(classOf[S])
    enhancer.setCallback(intercept)
    enhancer.create().asInstanceOf[S]
  }

  def addColumnIfNotExists(dbDriver: String, tableName: String, columName: String, default: String) = {
    if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"))
      s"""
         |IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '$tableName' AND COLUMN_NAME = '$columName')
         |BEGIN
         |    ALTER TABLE $tableName ADD $columName VARCHAR(255) DEFAULT '$default';
         |END""".stripMargin
    else
      s"""ALTER TABLE $tableName ADD COLUMN IF NOT EXISTS "$columName" character varying(255) DEFAULT '$default';""".stripMargin
  }


  def dropIndexIfExists(dbDriver: String, tableName: String, index: String) = {
    if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"))
      s"""
         |IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = '$index' AND object_id = OBJECT_ID('$tableName'))
         |BEGIN
         |    DROP INDEX $tableName.$index;
         |END""".stripMargin
    else
      s"""DROP INDEX IF EXISTS $index;""".stripMargin
  }


  def createIndexIfNotExists(dbDriver: String, tableName: String, index: String) = {
    if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"))
      s"""
         |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = '$index' AND object_id = OBJECT_ID('$tableName'))
         |BEGIN
         |    CREATE INDEX $index on $tableName(${index.split("_").drop(1).mkString(",")});
         |END""".stripMargin
    else
      s"CREATE INDEX IF NOT EXISTS $index on $tableName(${index.split("_").drop(1).mkString(",")});"
  }


  import java.util.Date
  import java.util.Calendar

  def calculateValidTo(
                        validFrom: Option[Date],
                        timeToLive: Long // milliseconds
                      ): Date = {
    val baseTime = validFrom.getOrElse(new Date())

    val calendar = Calendar.getInstance()
    calendar.setTime(baseTime)
    calendar.add(Calendar.SECOND, timeToLive.toInt / 1000)

    calendar.getTime
  }




}
