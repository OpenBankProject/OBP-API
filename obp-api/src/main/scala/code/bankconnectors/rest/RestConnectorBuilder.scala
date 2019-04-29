package code.bankconnectors.rest

import java.io.File
import java.util.Date

import code.api.util.CallContext
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

object RestConnectorBuilder extends App {

  //  val value  = this.getBanksFuture(None)
  //  val value2  = this.getBankFuture(BankId("hello-bank-id"), None)
  //  Thread.sleep(10000)
  val genMethodNames = List(
//    "getAdapterInfo",
    "getAdapterInfoFuture",
    //    "getUser", // have problem, return type not common
//    "getBanks",
    "getBanksFuture",
//    "getBank",
    "getBankFuture",
//    "getBankAccountsForUser",
    "getBankAccountsForUserFuture",
    "getCustomersByUserIdFuture",
//    "getBankAccount",
//    "checkBankAccountExists",
    "checkBankAccountExistsFuture",
//    "getCoreBankAccounts",
    "getCoreBankAccountsFuture",
//    "getTransactions",
//    "getTransactionsCore",
//    "getTransaction",
//    "getTransactionRequests210", //have problem params are not simple object
//    "getCounterparties",
//    "getCounterpartiesFuture",
//    "getCounterpartyByCounterpartyIdFuture",
//    "getCounterpartyTrait",
//    "getCheckbookOrdersFuture",
//    "getStatusOfCreditCardOrderFuture",
//    "getBranchesFuture",
//    "getBranchFuture",
//    "getAtmsFuture",
//    "getAtmFuture",
//    "getChallengeThreshold",
    
//    "makePaymentv210",//not support
//    "createChallenge",//not support
//    "createCounterparty" // not support
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
        case name if(name.matches("(get|check).*")) => GetGenerator(methodName, typeSignature)
        case name if(name.matches("(create|make).*")) => PostGenerator(methodName, typeSignature)
        case _ => throw new NotImplementedError(s" not support method name: $methodName")
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

  println(ReflectUtils.createDocExample(tp))
}

case class GetGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")

  private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString)

  private[this] val description = methodName.replace("Future", "").replaceAll("([a-z])([A-Z])", "$1 $2").capitalize
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isReturnBox = resultType.startsWith("Box[")

  private[this] val cachMethodName = if(isReturnBox) "memoizeSyncWithProvider" else "memoizeWithProvider"

  private[this] val outBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}"
    if(!ReflectUtils.isTypeExists(typeName)) typeName += "Future"
    val outBoundType = ReflectUtils.getTypeByName(typeName)
    ReflectUtils.createDocExample(outBoundType)
  }
  private[this] val inBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"
    if(!ReflectUtils.isTypeExists(typeName)) typeName += "Future"
    val inBoundType = ReflectUtils.getTypeByName(typeName)
    ReflectUtils.createDocExample(inBoundType)
  }

  val signature = s"$methodName$paramAnResult"
  val pathVariables = params.map(it => s""", ("$it", $it)""").mkString
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
       |    process = "obp.get.$returnEntityType",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = None,
       |    inboundTopic = None,
       |    exampleOutboundMessage = (
       |      $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |      $inBoundExample
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
       |        val url = getUrl("$methodName" $pathVariables)
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
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")

  private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString).mkString(",", ",", "")
  private[this] val description = methodName.replaceAll("([a-z])([A-Z])", "$1 $2").capitalize

  private[this] val entityName = methodName.replaceFirst("^[a-z]+", "")

  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isOBPReturnType = resultType.startsWith("OBPReturnType[")

  private[this] val outBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}"
    val outBoundType = ReflectUtils.getTypeByName(typeName)
    ReflectUtils.createDocExample(outBoundType)
  }
  private[this] val inBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"
    val inBoundType = ReflectUtils.getTypeByName(typeName)
    ReflectUtils.createDocExample(inBoundType)
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

  override def toString =
    s"""
       |messageDocs += MessageDoc(
       |    process = "obp.post.$entityName",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = None,
       |    inboundTopic = None,
       |    exampleOutboundMessage = (
       |      $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |      $inBoundExample
       |    ),
       |    adapterImplementation = Some(AdapterImplementation("- Core", 1))
       |  )
       |  // url example: $urlDemo
       |  override def $signature = {
       |    val url = getUrl("$methodName")
       |    val jsonStr = write(OutBound${methodName.capitalize}(buildOutboundAdapterCallContext(callContext) $params))
       |    sendPostRequest[InBound${methodName.capitalize}](url, callContext, jsonStr)
       |      .map{ boxedResult =>
       |      $lastMapStatement
       |    }
       |  }
    """.stripMargin
}


