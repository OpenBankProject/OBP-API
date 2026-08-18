package code.bankconnectors

import org.json4s._
import code.api.util.{CallContext, CustomJsonFormats, OBPQueryParam, OptionalFieldSerializer}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{InBoundTrait, OutInBoundTransfer}
import com.openbankproject.commons.model.TopicTrait
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.Full
import com.openbankproject.commons.util.json
import org.json4s.JsonDSL._
import org.json4s.{Formats, JObject, JValue}
import org.apache.commons.lang3.StringUtils
import java.lang.reflect.{InvocationHandler, Method}
import scala.concurrent.Future
import scala.reflect.ManifestFactory
import scala.reflect.runtime.universe

object ConnectorUtils {
  
  lazy val proxyConnector: Connector = {
    val excludeProxyMethods = Set("getDynamicEndpoints", "dynamicEntityProcess", "setAccountHolder", "updateUserAccountViewsOld")

    val intercept: InvocationHandler = new InvocationHandler {
      override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
        val originResult: AnyRef = method.invoke(LocalMappedConnector, args: _*)

        val methodName = method.getName
        val inBoundType: Option[Class[_]] = ReflectUtils.forClassOption(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
        if (!methodName.contains("$default$") && inBoundType.isDefined && !excludeProxyMethods.contains(methodName)) {
          deleteIgnoreFieldValue(originResult, inBoundType.orNull).asInstanceOf[AnyRef]
        } else {
          originResult
        }
      }
    }
    ConnectorProxy.create(intercept)
  }

  private def deleteIgnoreFieldValue(obj: Any, inBoundClass: Class[_]): Any = obj match {
    case x: Future[_] => x.map(deleteIgnoreFieldValue(_, inBoundClass))
    case x @(Full(v), _: Option[CallContext]) => x.copy(_1 = Full(deleteIgnoreFields(v, inBoundClass)))
    case x @(v, _: Option[CallContext]) => x.copy(_1 = deleteIgnoreFields(v, inBoundClass))
    case Full((v, cc: Option[CallContext])) => Full(deleteIgnoreFields(v, inBoundClass) -> cc)
    case Full(v) => Full(deleteIgnoreFields(v, inBoundClass))
    case v => deleteIgnoreFields(v, inBoundClass)
  }

  /**
   * Reshapes a connector result into the payload type its InBound DTO declares.
   *
   * The serialization below is json4s decompose, which writes a case class's CONSTRUCTOR
   * PARAMETERS and ignores its trait accessors. A row named after its columns - MappedBankAccount's
   * accountBalance / theAccountId, say - therefore produces JSON that BankAccountCommons cannot
   * read, and extraction fails with "No usable value for balance" rather than returning a partly
   * filled object. Converting first, through the same reflective sibling conversion the Converter
   * companions use, makes the JSON match by construction: toOther reads the trait accessors by the
   * target's parameter names.
   *
   * Anything without a usable sibling - a primitive, a type that is already the DTO's, an abstract
   * target - is passed through untouched, which is what happened to everything before.
   */
  private def toDeclaredPayloadType(obj: Any, inBoundClass: Class[_]): Any = {
    val dataType: Option[universe.Type] =
      ReflectUtils.classToType(inBoundClass).members
        .find(m => m.isMethod && m.name.decodedName.toString == "data")
        .map(_.asMethod.returnType)

    def convert(value: Any, tp: universe.Type): Any = value match {
      case null => null
      case _ if tp.typeSymbol.isAbstract => value
      case list: List[_] if tp.typeArgs.nonEmpty =>
        val elementType = tp.typeArgs.head
        if (elementType.typeSymbol.isAbstract) list
        else list.map(item => convert(item, elementType))
      case option: Option[_] if tp.typeArgs.nonEmpty =>
        option.map(item => convert(item, tp.typeArgs.head))
      case single =>
        scala.util.Try(ReflectUtils.toOther[Any](single, tp)).getOrElse(single)
    }

    dataType.map(tp => convert(obj, tp)).getOrElse(obj)
  }

  private def deleteIgnoreFields(obj: Any, inBoundClass:  Class[_]): Any = {
    implicit val formats: Formats = LocalMappedConnector.formats
    def processIgnoreFields(fields: List[String]): List[String] = fields.collect {
      case x if x.startsWith("data.") => StringUtils.substringAfter(x, "data.")
    }
    val payload = toDeclaredPayloadType(obj, inBoundClass)
    val zson = OptionalFieldSerializer.toIgnoreFieldJson(payload, ReflectUtils.classToType(inBoundClass), processIgnoreFields)

    val jObj: JValue = "data" -> zson

    val mainFest = ManifestFactory.classType[InBoundTrait[Any]](inBoundClass)
    jObj.extract[InBoundTrait[Any]](formats, mainFest).data
  }

}

object LocalMappedOutInBoundTransfer extends OutInBoundTransfer {
  private val ConnectorMethodRegex = "(?i)OutBound(.)(.+)".r
  private lazy val connector: Connector = LocalMappedConnector
  private val queryParamType = universe.typeOf[List[OBPQueryParam]]
  private val callContextType = universe.typeOf[Option[CallContext]]
  private implicit val formats: org.json4s.Formats = CustomJsonFormats.nullTolerateFormats

  override def transfer(outbound: TopicTrait): Future[InBoundTrait[_]] = {
    val connectorMethod: String = outbound.getClass.getSimpleName match {
      case ConnectorMethodRegex(x, y) => s"${x.toLowerCase()}$y"
      case x => x
    }
    val clazz = Class.forName(s"com.openbankproject.commons.dto.InBound${connectorMethod.capitalize}")
    implicit val inboundMainFest: Manifest[InBoundTrait[_]] = ManifestFactory.classType[InBoundTrait[_]](clazz)

    connector.callableMethods.get(connectorMethod) match {
      case None => Future.failed(new IllegalArgumentException(s"Outbound instance $outbound have no corresponding method in the ${connector.getClass.getSimpleName}"))
      case Some(method) =>
        val nameToValue = outbound.nameToValue.toMap
        val argNameToType: List[(String, universe.Type)] = method.paramLists.head.map(it => it.name.decodedName.toString.trim -> it.info)
        val connectorMethodArgs: List[Any] = argNameToType collect {
          case (_, tp) if tp <:< callContextType => None // For connector method parameter `callContext: Option[CallContext]`, just pass None
          case (_, tp) if tp <:< queryParamType =>
            val limit = nameToValue("limit").asInstanceOf[Int]
            val offset = nameToValue("offset").asInstanceOf[Int]
            val fromDate = nameToValue("fromDate").asInstanceOf[String]
            val toDate = nameToValue("toDate").asInstanceOf[String]
            val queryParams: List[OBPQueryParam] = OBPQueryParam.toOBPQueryParams(limit, offset, fromDate, toDate)
            queryParams
          case (name, _) => nameToValue(name)
        }

        val connectorResult = ReflectUtils.invokeMethod(connector, method, connectorMethodArgs: _*)
        val futureResult: Future[InBoundTrait[_]] = transferConnectorResult(connectorResult)
        futureResult
    }
  }

  private def transferConnectorResult(any: Any)(implicit inboundMainFest: Manifest[InBoundTrait[_]]): Future[InBoundTrait[_]] = any match {
    case x: Future[_] => x.map { it =>
      val dataJson = json.Extraction.decompose(getData(it))
      val inboundJson: JObject = "data" -> dataJson
      inboundJson.extract[InBoundTrait[_]](formats, inboundMainFest)
    }
    case x =>
      Future{
        val dataJson = json.Extraction.decompose(getData(x))
        val inboundJson: JObject = "data" -> dataJson
        inboundJson.extract[InBoundTrait[_]](formats, inboundMainFest)
      }
  }
  // connector methods return different type value, this method just extract value for InboundXX#data
  private def getData(any: Any): Any = any match {
    case (Full(v), _: Option[CallContext]) => v
    case (v, _: Option[CallContext]) => v
    case Full((v, _: Option[CallContext])) => v
    case Full(v) => v
    case v => v
  }
}
