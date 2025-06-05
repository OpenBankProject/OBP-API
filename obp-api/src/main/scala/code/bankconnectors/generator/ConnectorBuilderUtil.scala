package code.bankconnectors.generator

import code.api.util.CodeGenerateUtils.createDocExample
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils.uncapitalize

import java.io.File
import java.net.URL
import java.util.Date
import scala.jdk.CollectionConverters.enumerationAsScalaIteratorConverter
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

/**
 * this is util for Connector builders, this should never be called by product code.
 */
object ConnectorBuilderUtil {
  
  def getClassesFromPackage(packageName: String): List[Class[_]] = {
    val classLoader = Thread.currentThread().getContextClassLoader
    val path = packageName.replace('.', '/')
    val resources: Seq[URL] = classLoader.getResources(path).asScala.toSeq

    resources.flatMap { resource =>
      val file = new File(resource.toURI)
      if (file.isDirectory) {
        file.listFiles()
          .filter(_.getName.endsWith(".class"))
          .map(_.getName.stripSuffix(".class"))
          .map(className => Class.forName(s"$packageName.$className"))
      } else {
        Seq.empty
      }
    }.toList
  }
  
  
  // rewrite method code.webuiprops.MappedWebUiPropsProvider#getWebUiPropsValue, avoid access DB cause dataSource not found exception
  {
    import javassist.ClassPool
    val pool = ClassPool.getDefault
    //NOTE: MEMORY_USER this ctClass will be cached in ClassPool, it may load too many classes into heap. 
    val ctClass = pool.getCtClass("code.webuiprops.MappedWebUiPropsProvider$")
    val m = ctClass.getDeclaredMethod("getWebUiPropsValue")
    m.insertBefore("""return ""; """)
    ctClass.toClass
    // if(ctClass != null) ctClass.detach()
  }

  /**
   * //def getAdapterInfo(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = ??? 
   * //def validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[IbanChecker]] = ??? 
   *
   *  This method will only extract the first return class from the method, this is the OBP pattern. 
   *  so we can use it for generate the commons case class.
   *
   *  eg: getAdapterInfo -->  return InboundAdapterInfoInternal
   *  validateAndCheckIbanNumber -->return IbanChecker
   */
  def extractReturnModel(tp: ru.Type): ru.Type = {
    if (tp.typeArgs.isEmpty) {
      tp
    } else {
      extractReturnModel(tp.typeArgs(0))
    }
  }

  val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
  val clazz: ru.ClassSymbol = mirror.typeOf[Connector].typeSymbol.asClass
  val connectorDecls: MemberScope = mirror.typeOf[Connector].decls
  val connectorDeclsMethods: Iterable[Symbol] = connectorDecls.filter(symbol => {
    val isMethod = symbol.isMethod && !symbol.asMethod.isVal && !symbol.asMethod.isVar && !symbol.asMethod.isConstructor && !symbol.isProtected
    isMethod})
  val connectorDeclsMethodsReturnOBPRequiredType: Iterable[MethodSymbol] = connectorDeclsMethods
    .map(it => it.asMethod)
    .filter(it => {
      extractReturnModel(it.returnType).typeSymbol.fullName.matches("((code\\.|com.openbankproject\\.).+)|(scala\\.Boolean)") //to make sure, it returned the OBP class and Boolean.
    })
  
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  
  /*
    * generateMethods and buildMethods has the same function, only responseExpression parameter type
    * different, because overload method can't compile for different responseExpression parameter.
   */

  def generateMethods(connectorMethodNames: List[String], connectorCodePath: String, responseExpression: String,
                      setTopic: Boolean = false, doCache: Boolean = false) =
    buildMethods(connectorMethodNames, connectorCodePath, _ => responseExpression, setTopic, doCache)

  def buildMethods(connectorMethodNames: List[String], connectorCodePath: String, connectorMethodToResponse: String => String,
                   setTopic: Boolean = false, doCache: Boolean = false): Unit = {

     val nameSignature: Iterable[ConnectorMethodGenerator] = ru.typeOf[Connector].decls
      .filter(_.isMethod)
      .filter(it => connectorMethodNames.contains(it.name.toString))
      .map(it => {
        val (methodName, typeSignature) = (it.name.toString, it.typeSignature)
        ConnectorMethodGenerator(methodName, typeSignature)
      })

    // check whether some methods names are wrong typo
    if(connectorMethodNames.size > nameSignature.size) {
      val generatedMethodsNames = nameSignature.map(_.methodName).toSet
      val invalidMethodNames = connectorMethodNames.filterNot(generatedMethodsNames.contains(_))
      throw new IllegalArgumentException(s"Some methods not be supported, please check following methods: ${invalidMethodNames.mkString(", \n")}")
    }

    val codeList = nameSignature.map(_.toCode(connectorMethodToResponse, setTopic, doCache))

    //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
    //  println(symbols)
    println("-------------------")
    codeList.foreach(println(_))
    println("===================")

    val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), connectorCodePath)
    val source = FileUtils.readFileToString(path, "utf-8")
    val start = "//---------------- dynamic start -------------------please don't modify this line"
    val end   = "//---------------- dynamic end ---------------------please don't modify this line"
    val placeHolderInSource = s"""(?s)$start.+$end"""
    val currentTime = APIUtil.DateWithSecondsFormat.format(new Date())
    val insertCode =
      s"""$start
         |// ---------- created on $currentTime
         |${codeList.mkString}
         |// ---------- created on $currentTime
         |$end """.stripMargin
    val newSource = source.replaceFirst(placeHolderInSource, insertCode)
    FileUtils.writeStringToFile(path, newSource, "utf-8")
  }


  private case class ConnectorMethodGenerator(methodName: String, tp: Type) {
    private[this] def paramAnResult = tp.toString
      .replaceAll("""[.\w]+\.(\w+\.([A-Z]+\b|Value)\b)""", "$1") // two times replaceAll to delete package name, but keep enum type name
      .replaceAll("""([.\w]+\.){2,}(\w+\b)""", "$2")
      .replaceFirst("\\)", "): ")
      .replace("cardAttributeType: Value", "cardAttributeType: CardAttributeType.Value") // scala enum is bad for Reflection
      .replace("productAttributeType: Value", "productAttributeType: ProductAttributeType.Value") // scala enum is bad for Reflection
      .replace("accountAttributeType: Value", "accountAttributeType: AccountAttributeType.Value") // scala enum is bad for Reflection
      .replaceFirst("""\btype\b""", "`type`")

    private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString).mkString(", ", ", ", "").replaceFirst("""\btype\b""", "`type`")
    private[this] val description = methodName.replaceAll("""(\w)([A-Z])""", "$1 $2").capitalize

    private[this] val entityName = methodName.replaceFirst("^[a-z]+(OrUpdate)?", "")

    private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

    private[this] val isOBPReturnType = resultType.startsWith("OBPReturnType[")

    private[this] val outBoundExample = {
      var typeName = s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}"
      val outBoundType = ReflectUtils.getTypeByName(typeName)
      createDocExample(outBoundType).replaceAll("(?m)^(\\S)", "      $1")
    }
    private[this] val inBoundExample = {
      var typeName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"
      val inBoundType = ReflectUtils.getTypeByName(typeName)
      createDocExample(inBoundType).replaceAll("(?m)^(\\S)", "      $1")
    }

    var signature = s"$methodName$paramAnResult"

    val hasCallContext = tp.paramLists(0)
      .exists(_.asTerm.info =:= ru.typeOf[Option[CallContext]])

    /**
     * Get all the parameters name as a String from `typeSignature` object.
     * eg: it will return
     * , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutingScheme, accountRoutingAddress
     */
    private[this] val parametersNamesString = tp.paramLists(0)//paramLists will return all the curry parameters set.
      .filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]) // remove the `CallContext` field.
      .map(_.name.toString)//get all parameters name
      .map(it => if(it =="type") "`type`" else it)//This is special case for `type`, it is the keyword in scala.
      .map(it => if(it == "queryParams") "OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams)" else it)
    match {
      case Nil if hasCallContext => "callContext.map(_.toOutboundAdapterCallContext).orNull"
      case Nil => ""
      case list:List[String] if hasCallContext => list.mkString("callContext.map(_.toOutboundAdapterCallContext).orNull, ", ", ", "")
      case list:List[String] => list.mkString(", ")
    }

    // for cache
    private[this] val cacheMethodName = if(resultType.startsWith("Box[")) "memoizeSyncWithProvider" else "memoizeWithProvider"

    private[this] val timeoutFieldName = uncapitalize(methodName.replaceFirst("^[a-z]+", "")) + "TTL"
    private[this] val cacheTimeout = ReflectUtils.findMethod(ru.typeOf[code.bankconnectors.rabbitmq.RabbitMQConnector_vOct2024], timeoutFieldName)(_ => true)
      .map(_.name.toString)
      .getOrElse("accountTTL")

    // end for cache

    private val outBoundName = s"OutBound${methodName.capitalize}"
    private val inBoundName = s"InBound${methodName.capitalize}"

    val inboundDataFieldType = ReflectUtils.getTypeByName(s"com.openbankproject.commons.dto.$inBoundName")
      .member(TermName("data")).asMethod
      .returnType.toString.replaceAll(
      """(\w+\.)+(\w+\.Value)|(\w+\.)+(\w+)""", "$2$4"
    )

    def toCode(responseExpression: String => String, setTopic: Boolean = false, doCache: Boolean = false) = {
      val (outBoundTopic, inBoundTopic) =  setTopic match {
        case true =>
          (s"""Some(Topics.createTopicByClassName("$outBoundName").request)""" ,
           s"""Some(Topics.createTopicByClassName("$outBoundName").request)""" )
        case false => (None, None)
      }

      val callContext = if(hasCallContext) {
        ""
      } else {
        "\n        val callContext: Option[CallContext] = None"
      }

      var body =
      s"""|    import com.openbankproject.commons.dto.{$inBoundName => InBound, $outBoundName => OutBound}  $callContext
          |        val req = OutBound($parametersNamesString)
          |        val response: Future[Box[InBound]] = ${responseExpression(methodName)}
          |        response.map(convertToTuple[$inboundDataFieldType](callContext))        """.stripMargin


      if(doCache && methodName.matches("^(get|check|validate).+")) {
        signature = signature.replaceFirst("""(\b\S+)\s*:\s*Option\[CallContext\]""", "@CacheKeyOmit callContext: Option[CallContext]")
        body =
          s"""saveConnectorMetric {
             |    /**
             |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
             |      * is just a temporary value field with UUID values in order to prevent any ambiguity.
             |      * The real value will be assigned by Macro during compile time at this line of a code:
             |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
             |      */
             |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
             |    CacheKeyFromArguments.buildCacheKey {
             |      Caching.${cacheMethodName}(Some(cacheKey.toString()))($cacheTimeout seconds) {
             |
             |    ${body.replaceAll("(?m)^ ", "     ")}
             |
             |        }
             |      }
             |    }("$methodName")
             |""".stripMargin
      }
      s"""
         |  messageDocs += ${methodName}Doc
         |  def ${methodName}Doc = MessageDoc(
         |    process = "obp.$methodName",
         |    messageFormat = messageFormat,
         |    description = "$description",
         |    outboundTopic = $outBoundTopic,
         |    inboundTopic = $inBoundTopic,
         |    exampleOutboundMessage = (
         |    $outBoundExample
         |    ),
         |    exampleInboundMessage = (
         |    $inBoundExample
         |    ),
         |    adapterImplementation = Some(AdapterImplementation("- Core", 1))
         |  )
         |
         |  override def $signature = {
         |    $body
         |  }
          """.stripMargin
    }
  }

  //TODO WIP, need to fix the code to support the following methods
//  val commonMethodNames = LocalMappedConnector.callableMethods.keySet.toList

    val commonMethodNames = List(
      "getAdapterInfo",
      "getChallengeThreshold",
      "getChargeLevel",
      "getChargeLevelC2",
      "createChallenge",
      "getBank",
      "getBanks",
      "getBankAccountsForUser",
      "getBankAccountsBalances",
      "getBankAccountBalances",
      "getCoreBankAccounts",
      "getBankAccountsHeld",
      "getCounterpartyTrait",
      "getCounterpartyByCounterpartyId",
      "getCounterpartyByIban",
      "getCounterparties",
      "getTransactions",
      "getTransactionsCore",
      "getTransaction",
      "getPhysicalCardForBank",
      "deletePhysicalCardForBank",
      "getPhysicalCardsForBank",
      "createPhysicalCard",
      "updatePhysicalCard",
      "makePaymentv210",
      "makePaymentV400",
      "cancelPaymentV400",
      "createTransactionRequestv210",
      "getTransactionRequests210",
      "getTransactionRequestImpl",
      "createTransactionAfterChallengeV210",
      "updateBankAccount",
      "createBankAccount",
      "getBranch",
      "getBranches",
      "getAtm",
      "getAtms",
      "createTransactionAfterChallengev300",
      "makePaymentv300",
      "createTransactionRequestv300",
      "createCounterparty",
      "checkCustomerNumberAvailable",
      "createCustomer",
      "updateCustomerScaData",
      "updateCustomerCreditData",
      "updateCustomerGeneralData",
      "getCustomersByUserId",
      "getCustomerByCustomerId",
      "getCustomerByCustomerNumber",
      "getCustomerAddress",
      "createCustomerAddress",
      "updateCustomerAddress",
      "deleteCustomerAddress",
      "createTaxResidence",
      "getTaxResidence",
      "deleteTaxResidence",
      "getCustomers",
      "getCheckbookOrders",
      "getStatusOfCreditCardOrder",
      "createUserAuthContext",
      "createUserAuthContextUpdate",
      "deleteUserAuthContexts",
      "deleteUserAuthContextById",
      "getUserAuthContexts",
      "createOrUpdateProductAttribute",
      "getProduct",
      "getProducts",
      "getProductAttributeById",
      "getProductAttributesByBankAndCode",
      "deleteProductAttribute",
      "getAccountAttributeById",
      "createOrUpdateAccountAttribute",
      "createAccountAttributes",
      "getAccountAttributesByAccount",
      "createOrUpdateCardAttribute",
      "getCardAttributeById",
      "getCardAttributesFromProvider",
      "createAccountApplication",
      "getAllAccountApplication",
      "getAccountApplicationById",
      "updateAccountApplicationStatus",
      "getOrCreateProductCollection",
      "getProductCollection",
      "getOrCreateProductCollectionItem",
      "getProductCollectionItem",
      "getProductCollectionItemsTree",
      "createMeeting",
      "getMeetings",
      "getMeeting",
      "createOrUpdateKycCheck",
      "createOrUpdateKycDocument",
      "createOrUpdateKycMedia",
      "createOrUpdateKycStatus",
      "getKycChecks",
      "getKycDocuments",
      "getKycMedias",
      "getKycStatuses",
      "createMessage",
      "makeHistoricalPayment",
      "validateChallengeAnswer",
      //"getBankLegacy", // should not generate for Legacy methods
      //"getBanksLegacy", // should not generate for Legacy methods
      //"getBankAccountsForUserLegacy", // should not generate for Legacy methods
      //"getBankAccountLegacy", // should not generate for Legacy methods
      "getBankAccountByIban",
      "getBankAccountByRouting",
      "getBankAccounts",
      "checkBankAccountExists",
      //"getCoreBankAccountsLegacy", // should not generate for Legacy methods
      //"getBankAccountsHeldLegacy", // should not generate for Legacy methods
      //"checkBankAccountExistsLegacy", // should not generate for Legacy methods
      //"getCounterpartyByCounterpartyIdLegacy", // should not generate for Legacy methods
      //"getCounterpartiesLegacy", // should not generate for Legacy methods
      //"getTransactionsLegacy", // should not generate for Legacy methods
      //"getTransactionLegacy", // should not generate for Legacy methods
      //"createPhysicalCardLegacy", // should not generate for Legacy methods
      //"getCustomerByCustomerIdLegacy", // should not generate for Legacy methods

      "createChallenges",
      "createTransactionRequestv400",
      "createTransactionRequestSepaCreditTransfersBGV1",
      "createTransactionRequestPeriodicSepaCreditTransfersBGV1",
      "getCustomersByCustomerPhoneNumber",
      "getTransactionAttributeById",
      "createOrUpdateCustomerAttribute",
      "createOrUpdateTransactionAttribute",
      "getCustomerAttributes",
      "getCustomerIdsByAttributeNameValues",
      "getCustomerAttributesForCustomers",
      "getTransactionIdsByAttributeNameValues",
      "getTransactionAttributes",
      "getBankAttributesByBank",
      "getCustomerAttributeById",
      "createDirectDebit",
      "deleteCustomerAttribute",
      "getPhysicalCardsForUser",
      "getChallengesByBasketId",
      "createChallengesC2",
      "createChallengesC3",
      "getChallenge",
      "getChallengesByTransactionRequestId",
      "getChallengesByConsentId",
      "validateAndCheckIbanNumber",
      "validateChallengeAnswerC2",
      "validateChallengeAnswerC3",
      "validateChallengeAnswerC4",
      "validateChallengeAnswerC5",
      "validateChallengeAnswerV2",
      "getCounterpartyByIbanAndBankAccountId",
      "getChargeValue",
      "saveTransactionRequestTransaction",
      "saveTransactionRequestChallenge",
      "getTransactionRequestTypes",
      "updateAccountLabel",
      "getProduct",
      "saveTransactionRequestStatusImpl",
      "getTransactionRequestTypeCharges",
      "getAccountsHeld",
      "getAccountsHeldByUser",
      "getRegulatedEntities",
      "getRegulatedEntityByEntityId",
      "getBankAccountBalancesByAccountId",
      "getBankAccountsBalancesByAccountIds",
      "getBankAccountBalanceById",
      "createOrUpdateBankAccountBalance",
      "deleteBankAccountBalance",
    ).distinct

  /**
   * these connector methods have special parameter or return type
   */
  val specialMethods = List(
    "getStatus",
    "createOrUpdateBranch",
    "createOrUpdateBank",
    "createOrUpdateAtm",
    "createOrUpdateProduct",
    "createOrUpdateFXRate",
    "getCurrentFxRate",
    "getCounterpartyFromTransaction",
    "getCounterpartiesFromTransaction",
  ).distinct

  val omitMethods = List(
    "createOrUpdateAttributeDefinition", // should not be auto generated
    "deleteAttributeDefinition", // should not be auto generated
    "getAttributeDefinition", // should not be auto generated
    "createStandingOrder", // should not be auto generated
    //** the follow 5 methods should not be generated, should create manually
    "dynamicEntityProcess",
    "dynamicEndpointProcess",
    "createDynamicEndpoint",
    "getDynamicEndpoint",
    "getDynamicEndpoints",
    "checkExternalUserCredentials",// this is not a standard connector method.
    "checkExternalUserExists", // this is not a standard connector method. 
    "getBankAccountByRoutingLegacy",
    "getAccountRoutingsByScheme",
    "getAccountRouting",
    "getBankAccountsWithAttributes",
    "getBankSettlementAccounts",
    "getCountOfTransactionsFromAccountToCounterparty",
    "getStatus",
    "createOrUpdateBank",
    "createOrUpdateProduct",
    "getAllAtms",
    "getCurrentCurrencies",
    "getAgents",
    "getCustomersAtAllBanks",
    "createOrUpdateBankAttribute",
    "getBankAttribute",
    "createOrUpdateAtmAttribute",
    "getAtmAttribute",
    "getBankAttributeById",
    "getAtmAttributeById",
    "getUserAttributes",
    "getPersonalUserAttributes",
    "getNonPersonalUserAttributes",
    "getUserAttributesByUsers",
    "createOrUpdateUserAttribute",
    "getUserAttribute",
    "getUserAttributeById",
    "deleteUserAttribute",
    "getTransactionRequestIdsByAttributeNameValues",
    "sendCustomerNotification",
    "equals",
    "getAtmAttributesByAtm",
    "==",
    "!=",
  ).distinct
}


