package code.bankconnectors

import java.lang.reflect.Method

import code.api.util.{CallContext, CustomJsonFormats, OptionalFieldSerializer, OBPQueryParam}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{InBoundTrait, OutInBoundTransfer}
import com.openbankproject.commons.model.TopicTrait
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.Full
import net.liftweb.json
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JObject, JValue}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}
import org.apache.commons.lang3.StringUtils

import scala.concurrent.Future
import scala.reflect.runtime.universe
import scala.reflect.ManifestFactory

object ConnectorUtils {

  lazy val proxyConnector: Connector = {
    val excludeProxyMethods = Set("getDynamicEndpoints", "dynamicEntityProcess", "setAccountHolder", "updateUserAccountViewsOld")

    val intercept:MethodInterceptor = (_: Any, method: Method, args: Array[AnyRef], _: MethodProxy) => {
      val originResult: AnyRef = method.invoke(LocalMappedConnector, args:_*)


      val methodName = method.getName
      val inBoundType: Option[Class[_]] = ReflectUtils.forClassOption(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
      if (!methodName.contains("$default$") && inBoundType.isDefined && !excludeProxyMethods.contains(methodName)) {
        deleteIgnoreFieldValue(originResult, inBoundType.orNull).asInstanceOf[AnyRef]
      } else {
        originResult
      }

    }
    val enhancer: Enhancer = new Enhancer()
    enhancer.setSuperclass(classOf[Connector])
    enhancer.setCallback(intercept)
    enhancer.create().asInstanceOf[Connector]
  }

  private def deleteIgnoreFieldValue(obj: Any, inBoundClass: Class[_]): Any = obj match {
    case x: Future[_] => x.map(deleteIgnoreFieldValue(_, inBoundClass))
    case x @(Full(v), _: Option[CallContext]) => x.copy(_1 = Full(deleteIgnoreFields(v, inBoundClass)))
    case x @(v, _: Option[CallContext]) => x.copy(_1 = deleteIgnoreFields(v, inBoundClass))
    case Full((v, cc: Option[CallContext])) => Full(deleteIgnoreFields(v, inBoundClass) -> cc)
    case Full(v) => Full(deleteIgnoreFields(v, inBoundClass))
    case v => deleteIgnoreFields(v, inBoundClass)
  }

  private def deleteIgnoreFields(obj: Any, inBoundClass:  Class[_]): Any = {
    implicit val formats: Formats = LocalMappedConnector.formats
    def processIgnoreFields(fields: List[String]): List[String] = fields.collect {
      case x if x.startsWith("data.") => StringUtils.substringAfter(x, "data.")
    }
    val zson = OptionalFieldSerializer.toIgnoreFieldJson(obj, ReflectUtils.classToType(inBoundClass), processIgnoreFields)

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
  private implicit val formats = CustomJsonFormats.nullTolerateFormats

  override def transfer(outbound: TopicTrait): Future[InBoundTrait[_]] = {
    val connectorMethod: String = outbound.getClass.getSimpleName match {
      case ConnectorMethodRegex(x, y) => s"${x.toLowerCase()}$y"
      case x => x
    }
    val clazz = Class.forName(s"com.openbankproject.commons.dto.InBound${connectorMethod.capitalize}")
    implicit val inboundMainFest: Manifest[InBoundTrait[_]] = ManifestFactory.classType[InBoundTrait[_]](clazz)

    connector.implementedMethods.get(connectorMethod) match {
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
