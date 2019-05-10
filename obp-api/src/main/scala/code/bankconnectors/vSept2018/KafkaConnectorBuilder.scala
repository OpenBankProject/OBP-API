package code.bankconnectors.vSept2018

import java.io.File
import java.util.Date
import java.util.regex.Matcher

import code.api.util.{CallContext, OBPQueryParam}
import code.bankconnectors.Connector
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils._

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

object KafkaConnectorBuilder extends App {

  val genMethodNames = List(
    "getKycChecks",
    "getKycDocuments",
    "getKycMedias",
    "getKycStatuses",
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

  val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), "src/main/scala/code/bankconnectors/vSept2018/KafkaMappedConnector_vSept2018.scala")
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

  println(ReflectUtils.createDocExample(tp))
}

case class GetGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ").replaceFirst("""\btype\b""", "`type`")

  private[this] val params = tp.paramLists(0)
    .filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]])
    .map(_.name.toString) match {
    case Nil => ""
    case list:List[String] => list.mkString(", ", ", ", "")
  }


  private[this] val description = methodName.replace("Future", "").replaceAll("([a-z])([A-Z])", "$1 $2").capitalize
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  private[this] val isReturnBox = resultType.startsWith("Box[")

  private[this] val cachMethodName = if(isReturnBox) "memoizeSyncWithProvider" else "memoizeWithProvider"

  private[this] val timeoutFieldName = uncapitalize(methodName.replaceFirst("^[a-z]+", "")) + "TTL"
  private[this] val cacheTimeout = ReflectUtils.findMethod(ru.typeOf[KafkaMappedConnector_vSept2018], timeoutFieldName)(_ => true)
                                  .map(_.name.toString)
                                  .getOrElse("accountTTL")

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

  val outboundName = s"OutBound${methodName.capitalize}"
  val inboundName = s"InBound${methodName.capitalize}"

  override def toString =
    s"""
       |messageDocs += MessageDoc(
       |    process = "obp.$methodName",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).request),
       |    inboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).response),
       |    exampleOutboundMessage = (
       |      $outBoundExample
       |    ),
       |    exampleInboundMessage = (
       |      $inBoundExample
       |    ),
       |    adapterImplementation = Some(AdapterImplementation("- Core", 1))
       |  )
       |  override def $signature = saveConnectorMetric {
       |    import com.openbankproject.commons.dto.{${outboundName} => OutBound, ${inboundName} => InBound}
       |    /**
       |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
       |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
       |      * The real value will be assigned by Macro during compile time at this line of a code:
       |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
       |      */
       |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
       |    CacheKeyFromArguments.buildCacheKey {
       |      Caching.${cachMethodName}(Some(cacheKey.toString()))(${cacheTimeout} second){
       |        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get ${params})
       |        logger.debug(s"Kafka getKycDocuments Req is: $$req")
       |        processRequest[InBound](req) map (convertToTuple(callContext))
       |      }
       |    }
       |  }("$methodName")
    """.stripMargin
}

case class PostGenerator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ").replaceFirst("""\btype\b""", "`type`")

  private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString).mkString(", ", ", ", "").replaceFirst("""\btype\b""", "`type`")
  private[this] val description = methodName.replaceAll("([a-z])([A-Z])", "$1 $2").capitalize

  private[this] val entityName = methodName.replaceFirst("^[a-z]+(OrUpdate)?", "")

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

  val outboundName = s"OutBound${methodName.capitalize}"
  val inboundName = s"InBound${methodName.capitalize}"

  override def toString =
    s"""
       |messageDocs += MessageDoc(
       |    process = "obp.$methodName",
       |    messageFormat = messageFormat,
       |    description = "$description",
       |    outboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).request),
       |    inboundTopic = Some(Topics.createTopicByClassName(${outboundName}.getClass.getSimpleName).response),
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
       |    import net.liftweb.json.Serialization.write
       |
       |    val url = getUrl("$methodName")
       |    val outboundAdapterCallContext = Box(callContext.map(_.toOutboundAdapterCallContext)).openOrThrowException(NoCallContext)
       |    val jsonStr = write(OutBound${methodName.capitalize}(outboundAdapterCallContext $params))
       |    sendPostRequest[InBound${methodName.capitalize}](url, callContext, jsonStr)
       |      .map{ boxedResult =>
       |      $lastMapStatement
       |    }
       |  }
    """.stripMargin
}


