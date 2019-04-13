package code.bankconnectors.rest

import java.io.File
import java.util.Date

import code.bankconnectors.Connector
import com.openbankproject.commons.model._
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
    "getCustomersByUserIdFuture",
    //"getCounterpartiesFuture"
    //, "getTransactionsFuture", "getTransactionFuture", "getAdapterInfoFuture"
  )

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  private val nameSignature = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
    .map(it => Generator(it.name.toString, it.typeSignature))


  //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
  //  println(symbols)
  println("-------------------")
  nameSignature.map(_.methodCode).foreach(println(_))
  println("===================")

  val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), "src/main/scala/code/bankconnectors/rest/RestConnector_vMar2019.scala")
  val source = FileUtils.readFileToString(path)
  val placeHolderInSource = "//---------------- dynamic end ---------------------please don't modify this line"
  val insertCode =
    s"""
      |// ---------- create on ${new Date()}
      |${nameSignature.map(_.methodCode).mkString}
      |$placeHolderInSource
    """.stripMargin
  val newSource = source.replace(placeHolderInSource, insertCode)
  FileUtils.writeStringToFile(path, newSource)

  // to check whether example is correct.
  private val tp: ru.Type = reflectionUtils.getTypeByName("com.openbankproject.commons.dto.rest.InBoundGetProductCollectionItemsTree")

  println(reflectionUtils.createDocExample(tp))
}

case class Generator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")

  private[this] val params = tp.paramLists(0).dropRight(1).map(_.name.toString)
  private[this] val name = methodName.replaceFirst("Future$", "")
  private[this] val description = methodName.replace("Future", "").replaceAll("([a-z])([A-Z])", "$1 $2").capitalize
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val outBoundExample = {
    val outBoundType = reflectionUtils.getTypeByName(s"com.openbankproject.commons.dto.rest.OutBound${methodName.capitalize}")
    reflectionUtils.createDocExample(outBoundType)
  }
  private[this] val inBoundExample = {
    val inBoundType = reflectionUtils.getTypeByName(s"com.openbankproject.commons.dto.rest.InBound${methodName.capitalize}")
    println(inBoundType)
    reflectionUtils.createDocExample(inBoundType)
    //""
  }

  val signature = s"$methodName$paramAnResult"
  val pathVariables = params.map(it => s""", ("$it", $it)""").mkString
  val urlDemo = s"/$name" + params.map(it => s"/$it/{$it}").mkString
  val jsonType = "InBound" + methodName.capitalize


  val dataType = if(resultType.startsWith("Future[Box[")) {
    resultType.replaceFirst("""Future\[Box\[\((.+), Option\[CallContext\]\)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else {
    resultType.replaceFirst("""OBPReturnType\[Box\[(.+)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  }
  val returnEntityType = dataType.replaceFirst("Commons$", "").replaceAll(".*\\[|\\].*", "")

  val lastMapStatement = if (resultType.startsWith("Future[Box[")) {
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
  val methodCode =
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
       |      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
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



object reflectionUtils {
  private[this] val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)

  private[this] def genericSymboToString(tp: ru.Type): String = {
    if (tp.typeArgs.isEmpty) {
      createDocExample(tp)
    } else {
      val value = tp.typeArgs.map(genericSymboToString).mkString(",")
      s"${tp.typeSymbol.name}(${value})".replaceFirst("Tuple\\d*", "")
    }
  }

  def createDocExample(tp: ru.Type): String = {
    if (tp.typeSymbol.asClass.isCaseClass) {
      val fields = tp.decls.find(it => it.isConstructor).toList.flatMap(_.asMethod.paramLists(0)).foldLeft("")((str, symbol) => {
        val TypeRef(pre: Type, sym: Symbol, args: List[Type]) = symbol.info
        val value = if (pre <:< ru.typeOf[ProductAttributeType.type]) {
          "ProductAttributeType.STRING"
        } else if (pre <:< ru.typeOf[AccountAttributeType.type]) {
          "AccountAttributeType.INTEGER"
        } else if (args.isEmpty) {
          createDocExample(sym.asType.toType)
        } else {
          val typeParamStr = args.map(genericSymboToString).mkString(",")
          s"${sym.name}($typeParamStr)"
        }
        s"""$str,
           |${symbol.name}=${value}""".stripMargin
      }).substring(2)
      s"${tp.typeSymbol.name}($fields)"
    } else if (tp =:= ru.typeOf[String]) {
      """"string""""
    } else if (tp =:= ru.typeOf[Int] || tp =:= ru.typeOf[java.lang.Integer]) {
      "123"
    } else if (tp =:= ru.typeOf[Float] || tp =:= ru.typeOf[Double] || tp =:= ru.typeOf[java.lang.Float] || tp =:= ru.typeOf[java.lang.Double]) {
      "123.123"
    } else if (tp =:= ru.typeOf[Date]) {
      "new Date()"
    } else if (tp =:= ru.typeOf[Boolean] || tp =:= ru.typeOf[java.lang.Boolean]) {
      "true"
    } else if (tp =:= ru.typeOf[BigDecimal]) {
      "123.321"
    }else {
      throw new IllegalStateException(s"type $tp is not supported, please add this type to here.")
    }
  }

  def getTypeByName(typeName: String): ru.Type = mirror.staticClass(typeName).asType.toType

}