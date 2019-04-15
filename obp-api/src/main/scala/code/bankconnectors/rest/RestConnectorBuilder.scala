package code.bankconnectors.rest

import java.io.File
import java.util.Date

import code.bankconnectors.Connector
import code.util.reflectionUtils
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
    //"getAdapterInfoFuture", "getBanksFuture", "getBankFuture", "checkBankAccountExistsFuture", "getBankAccountFuture", "getCoreBankAccountsFuture",
    //  "getCustomersByUserIdFuture",
    //"getAdapterInfo",
    //"checkBankAccountExists",
    //"checkBankAccountExistsFuture"
    //"getCounterpartiesFuture",
    //"getTransactionsFuture", "getTransactionFuture", "getAdapterInfoFuture",
    "createChallenge",
    "createCounterparty",
    "makePaymentv210",
  )

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  private val nameSignature = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
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
  val placeHolderInSource = "//---------------- dynamic end ---------------------please don't modify this line"
  val insertCode =
    s"""
       |// ---------- create on ${new Date()}
       |${nameSignature.map(_.toString).mkString}
       |$placeHolderInSource
    """.stripMargin
  val newSource = source.replace(placeHolderInSource, insertCode)
  FileUtils.writeStringToFile(path, newSource)

  // to check whether example is correct.
  private val tp: ru.Type = reflectionUtils.getTypeByName("com.openbankproject.commons.dto.rest.InBoundGetProductCollectionItemsTree")

  println(reflectionUtils.createDocExample(tp))
}

case class GetGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")

  private[this] val params = tp.paramLists(0).dropRight(1).map(_.name.toString)

  private[this] val description = methodName.replace("Future", "").replaceAll("([a-z])([A-Z])", "$1 $2").capitalize
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isReturnBox = resultType.startsWith("Box[")

  private[this] val name = if(methodName.endsWith("Future")) {
    methodName.replaceFirst("Future$", "")
  } else {
    methodName + "B" // if method return type is Box, make different with Future type, add a "B" at method name last.
  }

  private[this] val cachMethodName = if(isReturnBox) "memoizeSyncWithProvider" else "memoizeWithProvider"

  private[this] val outBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.rest.OutBound${methodName.capitalize}"
    if(isReturnBox) typeName += "Future"
    val outBoundType = reflectionUtils.getTypeByName(typeName)
    reflectionUtils.createDocExample(outBoundType)
  }
  private[this] val inBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.rest.InBound${methodName.capitalize}"
    if(isReturnBox) typeName += "Future"
    val inBoundType = reflectionUtils.getTypeByName(typeName)
    reflectionUtils.createDocExample(inBoundType)
  }

  val signature = s"$methodName$paramAnResult"
  val pathVariables = params.map(it => s""", ("$it", $it)""").mkString
  val urlDemo = s"/$name" + params.map(it => s"/$it/{$it}").mkString
  val jsonType = if(isReturnBox) {
    "InBound" + methodName.capitalize + "Future"
  } else {
    "InBound" + methodName.capitalize
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
       |                         (result.data, buildCallContext(result.authInfo, callContext))
       |                    }
    """.stripMargin
  } else {
    """|                    boxedResult match {
       |                        case Full(result) => (Full(result.data), buildCallContext(result.authInfo, callContext))
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
       |        val url = getUrl("$name" $pathVariables)
       |        sendGetRequest[$jsonType](url, callContext)
       |          .map { boxedResult =>
       |             $lastMapStatement
       |          }
       |      }
       |    }
       |  }("$name")
    """.stripMargin
}

case class PostGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")

  private[this] val params = tp.paramLists(0).dropRight(1).map(_.name.toString)

  private[this] val description = methodName.replaceAll("([a-z])([A-Z])", "$1 $2").capitalize
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isOBPReturnType = resultType.startsWith("OBPReturnType[")

  private[this] val outBoundExample = {
    var typeName = s"com.openbankproject.commons.dto.rest.OutBound${methodName.capitalize}"
//    if(isOBPReturnType) typeName += "Future"
//    val outBoundType = reflectionUtils.getTypeByName(typeName)
//    reflectionUtils.createDocExample(outBoundType)
    ""
  }
  private[this] val inBoundExample = {
//    var typeName = s"com.openbankproject.commons.dto.rest.InBound${methodName.capitalize}"
//    if(isOBPReturnType) typeName += "Future"
//    val inBoundType = reflectionUtils.getTypeByName(typeName)
//    reflectionUtils.createDocExample(inBoundType)
    ""
  }

  val signature = s"$methodName$paramAnResult"
  val pathVariables = params.map(it => s""", ("$it", $it)""").mkString
  val urlDemo = s"/$methodName" + params.map(it => s"/$it/{$it}").mkString
  val jsonType = if(isOBPReturnType) {
    "InBound" + methodName.capitalize + "Future"
  } else {
    "InBound" + methodName.capitalize
  }


  val dataType = if (resultType.startsWith("Future[Box[")) {
    resultType.replaceFirst("""Future\[Box\[\((.+), Option\[CallContext\]\)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else if (resultType.startsWith("OBPReturnType[Box[")) {
    resultType.replaceFirst("""OBPReturnType\[Box\[(.+)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else if (isOBPReturnType) {
    //Box[(InboundAdapterInfoInternal, Option[CallContext])]
    resultType.replaceFirst("""Box\[\((.+), Option\[CallContext\]\)\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else {
    throw new NotImplementedError(s"this return type not implemented: $resultType")
  }
  val returnEntityType = dataType.replaceFirst("Commons$", "").replaceAll(".*\\[|\\].*", "")

  val lastMapStatement = if (isOBPReturnType || resultType.startsWith("Future[Box[")) {
    """|                    boxedResult.map { result =>
       |                         (result.data, buildCallContext(result.authInfo, callContext))
       |                    }
    """.stripMargin
  } else {
    """|                    boxedResult match {
       |                        case Full(result) => (Full(result.data), buildCallContext(result.authInfo, callContext))
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
       |  override def $signature = {
       |
       |  }
    """.stripMargin
}


