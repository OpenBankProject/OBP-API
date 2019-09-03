package code.bankconnectors.rest

import java.io.File
import java.util.Date

import code.api.util.{APIUtil, CallContext, OBPQueryParam}
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}
import code.api.util.CodeGenerateUtils.createDocExample

object RestConnectorBuilder extends App {
  // rewrite method code.webuiprops.MappedWebUiPropsProvider#getWebUiPropsValue, avoid access DB cause dataSource not found exception
  {
    import javassist.ClassPool
    val pool = ClassPool.getDefault
    val ct = pool.getCtClass("code.webuiprops.MappedWebUiPropsProvider$")
    val m = ct.getDeclaredMethod("getWebUiPropsValue")
    m.insertBefore("""return ""; """)
    ct.toClass
  }

  val genMethodNames = List(
    //    "getEmptyBankAccount", //not useful!
    //    "getCounterpartyFromTransaction", //not useful!
    //    "getCounterpartiesFromTransaction",//not useful!
    
    "getAdapterInfo",
    "getChallengeThreshold",
    "getChargeLevel",
    "createChallenge",
    "getBank",
    "getBanks",
    "getBankAccountsForUser",
    "getUser",
    "getBankAccount",
    "getBankAccount",
    "getBankAccountsBalances",
    "getCoreBankAccounts",
    "getBankAccountsHeld",
    "checkBankAccountExists",
    "getCounterparty",
    "getCounterpartyTrait",
    "getCounterpartyByCounterpartyId",
    "getCounterpartyByIban",
    "getCounterparties",
    "getTransactions",
    "getTransactionsCore",
    "getTransaction",
    "getPhysicalCards",
    "getPhysicalCardForBank",
    "deletePhysicalCardForBank",
    "getPhysicalCardsForBank",
    "createPhysicalCard",
    "updatePhysicalCard",
    "makePayment",
    "makePaymentv200",
    "makePaymentv210",
    "makePaymentImpl",
    "createTransactionRequest",
    "createTransactionRequestv200",
    "getStatus",
    "getChargeValue",
    "createTransactionRequestv210",
    "createTransactionRequestImpl",
    "createTransactionRequestImpl210",
    "saveTransactionRequestTransaction",
    "saveTransactionRequestTransactionImpl",
    "saveTransactionRequestChallenge",
    "saveTransactionRequestChallengeImpl",
    "saveTransactionRequestStatusImpl",
    "getTransactionRequests",
    "getTransactionRequests210",
    "getTransactionRequestStatuses",
    "getTransactionRequestStatusesImpl",
    "getTransactionRequestsImpl",
    "getTransactionRequestsImpl210",
    "getTransactionRequestImpl",
    "getTransactionRequestTypes",
    "getTransactionRequestTypesImpl",
    "answerTransactionRequestChallenge",
    "createTransactionAfterChallenge",
    "createTransactionAfterChallengev200",
    "createTransactionAfterChallengeV210",
    "updateBankAccount",
    "createBankAndAccount",
    "createBankAccount",
    "createSandboxBankAccount",
    "setAccountHolder",
    "accountExists",
    "removeAccount",
    "getMatchingTransactionCount",
    "createImportedTransaction",
    "updateAccountBalance",
    "setBankAccountLastUpdated",
    "updateAccountLabel",
    "updateAccount",
    "getProducts",
    "getProduct",
    "createOrUpdateBranch",
    "createOrUpdateBank",
    "createOrUpdateAtm",
    "createOrUpdateProduct",
    "createOrUpdateFXRate",
    "getBranch",
    "getBranches",
    "getAtm",
    "getAtms",
    "accountOwnerExists",
    "createViews",
    "getCurrentFxRate",
    "getCurrentFxRateCached",
    "getTransactionRequestTypeCharge",
    "UpdateUserAccoutViewsByUsername",
    "createTransactionAfterChallengev300",
    "makePaymentv300",
    "createTransactionRequestv300",
    "getTransactionRequestTypeCharges",
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
    // new removed comments
    "validateChallengeAnswer",
    "getBankLegacy",
    "getBanksLegacy",
    "getBankAccountsForUserLegacy",
    "updateUserAccountViewsOld",
    "getBankAccountLegacy",
    "getBankAccountByIban",
    "getBankAccountByRouting",
    "getBankAccounts",
    "getCoreBankAccountsLegacy",
    "getBankAccountsHeldLegacy",
    "checkBankAccountExistsLegacy",
    "getCounterpartyByCounterpartyIdLegacy",
    "getCounterpartiesLegacy",
    "getTransactionsLegacy",
    "getTransactionLegacy",
    "getPhysicalCardsForBankLegacy",
    "createPhysicalCardLegacy",
    "createBankAccountLegacy",
    "getBranchLegacy",
    "getAtmLegacy",
    "getCustomerByCustomerIdLegacy",
  )

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  private val nameSignature = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
    .filter(it => {
      it.typeSignature.paramLists(0).find(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).isDefined
    })
    .map(it => {
      val (methodName, typeSignature) = (it.name.toString, it.typeSignature)
      methodName match {
        //        case name if(name.matches("(get|check).*")) => GetGenerator(methodName, typeSignature)
        //        case name if(name.matches("(create|make).*")) => PostGenerator(methodName, typeSignature)
        case _ => PostGenerator(methodName, typeSignature)//throw new NotImplementedError(s" not support method name: $methodName")
      }

    })


  //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
  //  println(symbols)
  println("-------------------")
  nameSignature.map(_.toString).foreach(println(_))
  println("===================")

  val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), "src/main/scala/code/bankconnectors/rest/RestConnector_vMar2019.scala")
  val source = FileUtils.readFileToString(path)
  val start = "//---------------- dynamic start -------------------please don't modify this line"
  val end   = "//---------------- dynamic end ---------------------please don't modify this line"
  val placeHolderInSource = s"""(?s)$start.+$end"""
  val insertCode =
    s"""
       |$start
       |// ---------- create on ${new Date()}
       |${nameSignature.map(_.toString).mkString}
       |$end
    """.stripMargin
  val newSource = source.replaceFirst(placeHolderInSource, insertCode)
  FileUtils.writeStringToFile(path, newSource)

  // to check whether example is correct.
  private val tp: ru.Type = ReflectUtils.getTypeByName("com.openbankproject.commons.dto.InBoundGetProductCollectionItemsTree")

  println(createDocExample(tp))
}

case class GetGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString
    .replaceAll("(\\w+\\.)+", "")
    .replaceFirst("\\)", "): ")
    .replaceFirst("""\btype\b""", "`type`")
    .replace("cardAttributeType: Value", "cardAttributeType: CardAttributeType.Value") // scala enum is bad for Reflection
    .replace("productAttributeType: Value", "productAttributeType: ProductAttributeType.Value") // scala enum is bad for Reflection
    .replace("accountAttributeType: Value", "accountAttributeType: AccountAttributeType.Value") // scala enum is bad for Reflection
    .replaceFirst("""callContext:\s*Option\[CallContext\]""", "@CacheKeyOmit callContext: Option[CallContext]")

  private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString)

  private[this] val description = methodName.replace("Future", "").replaceAll("([a-z])([A-Z])", "$1 $2").capitalize
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isReturnBox = resultType.startsWith("Box[")

  private[this] val cachMethodName = if(isReturnBox) "memoizeSyncWithProvider" else "memoizeWithProvider"

  private[this] val outBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}"
    if(!ReflectUtils.isTypeExists(typeName)) typeName += "Future"
    val outBoundType = ReflectUtils.getTypeByName(typeName)
    createDocExample(outBoundType).replaceAll("(?m)^(\\S)", "      $1")
  }
  private[this] val inBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"
    if(!ReflectUtils.isTypeExists(typeName)) typeName += "Future"
    val inBoundType = ReflectUtils.getTypeByName(typeName)
    createDocExample(inBoundType).replaceAll("(?m)^(\\S)", "      $1")
  }

  val signature = s"$methodName$paramAnResult"

  val pathVariables = tp.paramLists(0) //For a method or poly type, a list of its value parameter sections.
    .filterNot(_.info =:= ru.typeOf[Option[CallContext]])
    .map { it =>
      // make sure if param signature is: queryParams: List[OBPQueryParam] , the param name must be queryParams
      val paramName = if(it.info <:< typeOf[Seq[OBPQueryParam]]) "queryParams" else it.name.toString
      val paramValue = it.name.toString
      s""", ("$paramName", $paramValue)"""
    }.mkString

  val urlDemo = s"/$methodName" + params.map(it => s"/$it/{$it}").mkString
  val jsonType = {
    val typeName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"
    if(ReflectUtils.isTypeExists(typeName)) {
      s"InBound${methodName.capitalize}"
    }
    else {
      s"InBound${methodName.capitalize}Future"
    }
  }


  val dataType = if (resultType.startsWith("Future[Box[")) {
    resultType.replaceFirst("""Future\[Box\[\((.+), Option\[CallContext\]\)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else if (resultType.startsWith("OBPReturnType[Box[")) {
    resultType.replaceFirst("""OBPReturnType\[Box\[(.+)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else if (isReturnBox) {
    //Box[(InboundAdapterInfoInternal, Option[CallContext])]
    resultType.replaceFirst("""Box\[\((.+), Option\[CallContext\]\)\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else {
    throw new NotImplementedError(s"this return type not implemented: $resultType")
  }
  val returnEntityType = dataType.replaceFirst("Commons$", "").replaceAll(".*\\[|\\].*", "")

  val lastMapStatement = if (isReturnBox || resultType.startsWith("Future[Box[")) {
    """|                    boxedResult.map { result =>
       |                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
       |                    }
    """.stripMargin
  } else {
    """|                    boxedResult match {
       |                        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
       |                        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
       |                    }
    """.stripMargin
  }

  override def toString =
    s"""
       |messageDocs += MessageDoc(
       |    process = "obp.$methodName",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = None,
       |    inboundTopic = None,
       |    exampleOutboundMessage = (
       |    $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |    $inBoundExample
       |    ),
       |    adapterImplementation = Some(AdapterImplementation("- Core", 1))
       |  )
       |  // url example: $urlDemo
       |  override def $signature = saveConnectorMetric {
       |    /**
       |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
       |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
       |      * The real value will be assigned by Macro during compile time at this line of a code:
       |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
       |      */
       |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
       |    CacheKeyFromArguments.buildCacheKey {
       |      Caching.${cachMethodName}(Some(cacheKey.toString()))(banksTTL second){
       |        val url = ""//getUrl(callContext, "$methodName" $pathVariables)
       |        sendGetRequest[$jsonType](url, callContext)
       |          .map { boxedResult =>
       |             $lastMapStatement
       |          }
       |      }
       |    }
       |  }("$methodName")
    """.stripMargin
}

case class PostGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString
    .replaceAll("(\\w+\\.)+", "")
    .replaceFirst("\\)", "): ")
    .replace("cardAttributeType: Value", "cardAttributeType: CardAttributeType.Value") // scala enum is bad for Reflection
    .replace("productAttributeType: Value", "productAttributeType: ProductAttributeType.Value") // scala enum is bad for Reflection
    .replace("accountAttributeType: Value", "accountAttributeType: AccountAttributeType.Value") // scala enum is bad for Reflection
    .replaceFirst("""\btype\b""", "`type`")

  private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString).mkString(", ", ", ", "").replaceFirst("""\btype\b""", "`type`")
  private[this] val description = methodName.replaceAll("([a-z])([A-Z])", "$1 $2").capitalize

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

  val signature = s"$methodName$paramAnResult"
  val urlDemo = s"/$methodName"

  val lastMapStatement = if (isOBPReturnType) {
    """|boxedResult match {
       |        case Full(result) => (Full(result.data), buildCallContext(result.inboundAdapterCallContext, callContext))
       |        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
       |      }
    """.stripMargin
  } else {
    """|boxedResult.map { result =>
       |          (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
       |        }
    """.stripMargin
  }
  val httpMethod = APIUtil.getRequestTypeByMethodName(methodName) match {
    case "get" => "HttpMethods.GET"
    case "post" => "HttpMethods.POST"
    case "put" => "HttpMethods.PUT"
    case "delete" => "HttpMethods.DELETE"
  }

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
    case Nil => ""
    case list:List[String] => list.mkString(", ", ", ", "")
  }

  val inboundDataFieldType = ReflectUtils.getTypeByName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
    .member(TermName("data")).asMethod
    .returnType.toString.replaceAll(
    """(\w+\.)+(\w+\.Value)|(\w+\.)+(\w+)""", "$2$4"
  )

  override def toString =
    s"""
       |  messageDocs += ${methodName}Doc
       |  def ${methodName}Doc = MessageDoc(
       |    process = "obp.$methodName",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = None,
       |    inboundTopic = None,
       |    exampleOutboundMessage = (
       |    $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |    $inBoundExample
       |    ),
       |    adapterImplementation = Some(AdapterImplementation("- Core", 1))
       |  )
       |  // url example: $urlDemo
       |  override def $signature = {
       |        import com.openbankproject.commons.dto.{OutBound${methodName.capitalize} => OutBound, InBound${methodName.capitalize} => InBound}
       |        val url = getUrl(callContext, "$methodName")
       |        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull $parametersNamesString)
       |        val result: OBPReturnType[Box[$inboundDataFieldType]] = sendRequest[InBound](url, $httpMethod, req, callContext).map(convertToTuple(callContext))
       |        result
       |  }
    """.stripMargin
}
