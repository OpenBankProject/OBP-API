package code.bankconnectors

import java.lang.reflect.Method

import code.api.util.{CallContext, FieldIgnoreSerializer}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.InBoundTrait
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.Full
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JValue}
import net.sf.cglib.proxy.{Enhancer, MethodInterceptor, MethodProxy}
import org.apache.commons.lang3.StringUtils

import scala.concurrent.Future
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
    val zson = FieldIgnoreSerializer.toIgnoreFieldJson(obj, ReflectUtils.classToType(inBoundClass), processIgnoreFields)

    val jObj: JValue = "data" -> zson

    val mainFest = ManifestFactory.classType[InBoundTrait[Any]](inBoundClass)
    jObj.extract[InBoundTrait[Any]](formats, mainFest).data
  }

}
