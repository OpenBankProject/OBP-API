package code.bankconnectors.storedprocedure

import java.io.File
import java.util.Date

import code.api.util.APIUtil.AdapterImplementation
import code.api.util.CallContext
import code.api.util.CodeGenerateUtils.createDocExample
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.util.StringHelpers
import org.apache.commons.io.FileUtils

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}
import scala.util.Random

object StoredProcedureConnectorBuilder extends App {
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
    "createTransactionRequestv210",
    "createTransactionRequestImpl",
    "createTransactionRequestImpl210",
    "getTransactionRequests",
    "getTransactionRequests210",
    "getTransactionRequestsImpl",
    "getTransactionRequestsImpl210",
    "getTransactionRequestImpl",
    "getTransactionRequestTypesImpl",
    "createTransactionAfterChallenge",
    "createTransactionAfterChallengev200",
    "createTransactionAfterChallengeV210",
    "updateBankAccount",
    "createBankAccount",
    "getProducts",
    "getProduct",
    "createOrUpdateBank",
    "createOrUpdateProduct",
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
    "getBankLegacy",
    "getBanksLegacy",
    "getBankAccountsForUserLegacy",
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

    //** not support methods:
    //"getStatus",
    //"getChargeValue",
    //"saveTransactionRequestTransaction",
    //"saveTransactionRequestTransactionImpl",
    //"saveTransactionRequestChallenge",
    //"saveTransactionRequestChallengeImpl",
    //"saveTransactionRequestStatusImpl",
    //"getTransactionRequestStatuses",
    //"getTransactionRequestStatusesImpl",
    // "getTransactionRequestTypes", // final method cant be override
    //"answerTransactionRequestChallenge",
    // "createBankAndAccount",
    // "createSandboxBankAccount",
    // "setAccountHolder",
    // "accountExists",
    // "removeAccount",
    // "getMatchingTransactionCount",
    // "createImportedTransaction",
    // "updateAccountBalance",
    // "setBankAccountLastUpdated",
    // "updateAccountLabel",
    // "updateAccount",
    // "createOrUpdateBranch",
    // "createOrUpdateAtm",
    // "createOrUpdateFXRate",
    // "accountOwnerExists",
    // "createViews",
    // "getCurrentFxRate",
    // "getCurrentFxRateCached",
    // "getTransactionRequestTypeCharge",
    // "UpdateUserAccoutViewsByUsername",
    // "getTransactionRequestTypeCharges",
    //"updateUserAccountViewsOld",

    //    "getEmptyBankAccount", //not useful!
    //    "getCounterpartyFromTransaction", //not useful!
    //    "getCounterpartiesFromTransaction",//not useful!
  )

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)

  /*
  find missing OutBound types
  val a = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
    .map(_.name.toString)
    .map(it => s"com.openbankproject.commons.dto.OutBound${it.capitalize}")
    .filter(it => {
      try {
        ReflectUtils.getTypeByName(it)
        false
      } catch {
        case _: Throwable => true
      }
    }).foreach(it => println(it.replace("com.openbankproject.commons.dto.OutBound", "")))
*/

  private val nameSignature = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
    .map(it => {
      val (methodName, typeSignature) = (it.name.toString, it.typeSignature)
      MethodBodyGenerator(methodName, typeSignature)
    })


  //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
  //  println(symbols)
  println("-------------------")
  nameSignature.map(_.toString).foreach(println(_))
  println("===================")

  val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), "src/main/scala/code/bankconnectors/storedprocedure/MsStoredProcedureConnector_vDec2019.scala")
  val source = FileUtils.readFileToString(path, "utf-8")
  val start = "//---------------- dynamic start -------------------please don't modify this line"
  val end   = "//---------------- dynamic end ---------------------please don't modify this line"
  val placeHolderInSource = s"""(?s)$start.+$end"""
  val insertCode =
    s"""$start
        |// ---------- create on ${new Date()}
        |${nameSignature.map(_.toString).mkString}
        |$end """.stripMargin
  val newSource = source.replaceFirst(placeHolderInSource, insertCode)
  FileUtils.writeStringToFile(path, newSource, "utf-8")

  // to check whether example is correct.
  private val tp: ru.Type = ReflectUtils.getTypeByName("com.openbankproject.commons.dto.InBoundGetProductCollectionItemsTree")

  println(createDocExample(tp))
}


case class MethodBodyGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString
    .replaceAll("(\\w+\\.)+", "")
    .replaceFirst("\\)", "): ")
    .replace("cardAttributeType: Value", "cardAttributeType: CardAttributeType.Value") // scala enum is bad for Reflection
    .replace("productAttributeType: Value", "productAttributeType: ProductAttributeType.Value") // scala enum is bad for Reflection
    .replace("accountAttributeType: Value", "accountAttributeType: AccountAttributeType.Value") // scala enum is bad for Reflection
    .replaceFirst("""\btype\b""", "`type`")

  private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString).mkString(", ", ", ", "").replaceFirst("""\btype\b""", "`type`")

  private val procedureName = StringHelpers.snakify(methodName)

  private[this] val description = s"""
      ||               |${methodName.replaceAll("([a-z])([A-Z])", "$1 $2").capitalize}
      ||               |
      ||               |The connector name is: stored_procedure_vDec2019
      ||               |The MS SQL Server stored procedure name is: $procedureName
      """


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

  val callContextVal: String = if(tp.paramLists(0).find(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).isEmpty) {
    "val callContext: Option[CallContext] = None"
  } else ""

  val randomNum = Random.nextInt(100)
  override def toString =
    s"""
       |  messageDocs += ${methodName}Doc$randomNum
       |  private def ${methodName}Doc$randomNum = MessageDoc(
       |    process = "obp.$methodName",
       |    messageFormat = messageFormat,
       |    description = \"\"\"${description}\"\"\".stripMargin,
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
       |  // stored procedure name: $procedureName
       |  override def $signature = {
       |        import com.openbankproject.commons.dto.{OutBound${methodName.capitalize} => OutBound, InBound${methodName.capitalize} => InBound}
       |        val procedureName = "$procedureName"
       |        $callContextVal
       |        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull $parametersNamesString)
       |        val result: OBPReturnType[Box[$inboundDataFieldType]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
       |        result
       |  }
    """.stripMargin
}
