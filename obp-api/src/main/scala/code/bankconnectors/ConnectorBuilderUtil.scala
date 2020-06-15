package code.bankconnectors

import java.io.File
import java.util.Date

import code.api.util.CallContext
import code.api.util.CodeGenerateUtils.createDocExample
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils.uncapitalize

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

/**
 * this is util for Connector builders, this should never be called by product code.
 */
object ConnectorBuilderUtil {
  // rewrite method code.webuiprops.MappedWebUiPropsProvider#getWebUiPropsValue, avoid access DB cause dataSource not found exception
  {
    import javassist.ClassPool
    val pool = ClassPool.getDefault
    val ct = pool.getCtClass("code.webuiprops.MappedWebUiPropsProvider$")
    val m = ct.getDeclaredMethod("getWebUiPropsValue")
    m.insertBefore("""return ""; """)
    ct.toClass
  }

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)

  def generateMethods(connectorMethodNames: List[String], connectorCodePath: String, responseExpression: String,
                      setTopic: Boolean = false, doCache: Boolean = false) =
    buildMethods(connectorMethodNames, connectorCodePath, _ => responseExpression, setTopic, doCache)

  def buildMethods(connectorMethodNames: List[String], connectorCodePath: String, connectorMethodToResponse: String => String,
                   setTopic: Boolean = false, doCache: Boolean = false): Unit = {

     val nameSignature: Iterable[ConnectorMethodGenerator] = ru.typeOf[Connector].decls
      .filter(_.isMethod)
      .filter(it => connectorMethodNames.contains(it.name.toString))
      .filter(it => {
        it.typeSignature.paramLists(0).exists(_.asTerm.info =:= ru.typeOf[Option[CallContext]])
      })
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
    val insertCode =
      s"""$start
         |// ---------- create on ${new Date()}
         |${codeList.mkString}
         |// ---------- create on ${new Date()}
         |$end """.stripMargin
    val newSource = source.replaceFirst(placeHolderInSource, insertCode)
    FileUtils.writeStringToFile(path, newSource, "utf-8")
  }


  private case class ConnectorMethodGenerator(methodName: String, tp: Type) {
    private[this] def paramAnResult = tp.toString
      .replaceAll("(\\w+\\.)+", "")
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

    // for cache
    private[this] val cacheMethodName = if(resultType.startsWith("Box[")) "memoizeSyncWithProvider" else "memoizeWithProvider"

    private[this] val timeoutFieldName = uncapitalize(methodName.replaceFirst("^[a-z]+", "")) + "TTL"
    private[this] val cacheTimeout = ReflectUtils.findMethod(ru.typeOf[KafkaMappedConnector_vSept2018], timeoutFieldName)(_ => true)
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

      val missingCallContext = if(tp.paramLists(0) //if parameter have no callContext, add None to Body
        .exists(_.asTerm.info =:= ru.typeOf[Option[CallContext]])) {
        ""
      } else {
        "val callContext: Option[CallContext] = None \n"
      }

      var body =
      s"""|    import com.openbankproject.commons.dto.{$outBoundName => OutBound, $inBoundName => InBound}
          |        ${missingCallContext}val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull $parametersNamesString)
          |        val response: Future[Box[InBound]] = ${responseExpression(methodName)}
          |        response.map(convertToTuple[$inboundDataFieldType](callContext))        """.stripMargin


      if(doCache && methodName.matches("^(get|check|validate).+")) {
        signature = signature.replaceFirst("""(\b\S+)\s*:\s*Option\[CallContext\]""", "@CacheKeyOmit callContext: Option[CallContext]")
        body =
          s"""saveConnectorMetric {
             |    /**
             |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
             |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
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
}


