package code.bankconnectors.vMay2019

import java.io.File
import java.util.Date
import java.util.regex.Matcher

import code.api.util.ApiTag.ResourceDocTag
import code.api.util.{ApiTag, CallContext, OBPQueryParam}
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils._

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

import code.api.util.CodeGenerateUtils.createDocExample

object KafkaConnectorBuilder extends App {

  val genMethodNames = List(
    "getAdapterInfo",
    "getBanks",
    "getBank",
    "getBankAccountsForUser",
    "getCustomersByUserId",
    "getCoreBankAccounts",
    "checkBankAccountExists",
    "getBankAccount",
    "getTransactions",
    "getTransaction",
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
    .map(it => (it.name.toString, it.typeSignature))
    .map{
      case (methodName, typeSignature) if(methodName.matches("^(get|check).+")) => GetGenerator(methodName, typeSignature)
      case other => new CommonGenerator(other._1, other._2)
    }

  if(genMethodNames.size > nameSignature.size) {
    val foundMehotdNames = nameSignature.map(_.methodName).toList
    val notFoundMethodNames = genMethodNames.filterNot(foundMehotdNames.contains(_))
    throw new IllegalArgumentException(s"some method not found, please check typo: ${notFoundMethodNames.mkString(", ")}")
  }

  //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
  //  println(symbols)
  println("-------------------")
  nameSignature.map(_.toString).foreach(println(_))
  println("===================") 

  val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), "src/main/scala/code/bankconnectors/vMay2019/KafkaMappedConnector_vMay2019.scala")
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
  val newSource = source.replaceFirst(placeHolderInSource, Matcher.quoteReplacement(insertCode))
  FileUtils.writeStringToFile(path, newSource)

  // to check whether example is correct.
  private val tp: ru.Type = ReflectUtils.getTypeByName("com.openbankproject.commons.dto.InBoundGetProductCollectionItemsTree")

  println(createDocExample(tp))
}

class CommonGenerator(val methodName: String, tp: Type) {
  protected[this] def paramAnResult = tp.toString
    .replaceAll("(\\w+\\.)+", "")
    .replaceFirst("\\)", "): ")
    .replaceFirst("""\btype\b""", "`type`")

  val queryParamsListName = tp.paramLists(0).find(symbol => symbol.info <:< typeOf[List[OBPQueryParam]]).map(_.name.toString)

  private[this] val params = tp.paramLists(0)
    .filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]])
    .map(_.name.toString)
    .map(it => if(it =="type") "`type`" else it)
    match {
    case Nil => ""
    case list:List[String] => {
      val paramNames = list.mkString(", ", ", ", "")
      queryParamsListName match {
        // deal with queryParams: List[OBPQueryParam], convert to four parameters
        case Some(queryParams) => paramNames.replaceFirst(
          s"""\\b(${queryParams})\\b""",
          "OBPQueryParam.getLimit($1), OBPQueryParam.getOffset($1), OBPQueryParam.getFromDate($1), OBPQueryParam.getToDate($1)"
        )
        case scala.None => paramNames
      }
    }
  }

  private[this] val description = methodName.replace("Future", "").replaceAll("([a-z])([A-Z])", "$1 $2").capitalize

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

  val outboundName = s"OutBound${methodName.capitalize}"
  val inboundName = s"InBound${methodName.capitalize}"

  val tagValues = ReflectUtils.getType(ApiTag).decls
    .filter(it => it.isMethod && it.asMethod.returnType <:< typeOf[ResourceDocTag])
    .map(_.asMethod)
    .filter(method => method.paramLists.isEmpty)
    .map(method => ReflectUtils.invokeMethod(ApiTag, method))
    .map(_.asInstanceOf[ResourceDocTag])
    .map(_.tag)
    .map(it => (it.replaceAll("""\W""", "").toLowerCase, it))
    .toMap

  val tag = tagValues.filter(it => methodName.toLowerCase().contains(it._1)).map(_._2).toList.sortBy(_.size).lastOption.getOrElse("- Core")

  val queryParamList = params.replaceFirst("""\b(.+?):\s*List[OBPQueryParam]""", "OBPQueryParam.getLimit($1), OBPQueryParam.getOffset($1), OBPQueryParam.getFromDate($1), OBPQueryParam.getToDate(queryParams)")

  protected[this] def methodBody: String =
    s"""{
      |    import com.openbankproject.commons.dto.{${outboundName} => OutBound, ${inboundName} => InBound}
      |
      |    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get ${params})
      |    logger.debug(s"Kafka ${methodName} Req is: $$req")
      |    processRequest[InBound](req) map (convertToTuple(callContext))
      |  }
    """.stripMargin

  override def toString =
    s"""
       |  messageDocs += MessageDoc(
       |    process = s"obp.$${nameOf($methodName _)}",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).request),
       |    inboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).response),
       |    exampleOutboundMessage = (
       |    $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |    $inBoundExample
       |    ),
       |    adapterImplementation = Some(AdapterImplementation("${tag}", 1))
       |  )
       |  override def $signature = ${methodBody}
    """.stripMargin
}

case class GetGenerator(override val methodName: String, tp: Type) extends CommonGenerator(methodName, tp) {
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isReturnBox = resultType.startsWith("Box[")

  private[this] val cachMethodName = if(isReturnBox) "memoizeSyncWithProvider" else "memoizeWithProvider"

  private[this] val timeoutFieldName = uncapitalize(methodName.replaceFirst("^[a-z]+", "")) + "TTL"
  private[this] val cacheTimeout = ReflectUtils.findMethod(ru.typeOf[KafkaMappedConnector_vMay2019], timeoutFieldName)(_ => true)
    .map(_.name.toString)
    .getOrElse("accountTTL")

  override def paramAnResult = super.paramAnResult
      .replaceFirst("""callContext:\s*Option\[CallContext\]""", "@CacheKeyOmit callContext: Option[CallContext]")

  override protected[this] def methodBody: String =
    s"""saveConnectorMetric {
      |    /**
      |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      |      * The real value will be assigned by Macro during compile time at this line of a code:
      |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      |      */
      |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      |    CacheKeyFromArguments.buildCacheKey {
      |      Caching.${cachMethodName}(Some(cacheKey.toString()))(${cacheTimeout} second) ${super.methodBody.replaceAll("(?m)^ ", "     ")}
      |    }
      |  }("$methodName")
    """.stripMargin
}




