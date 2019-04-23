package code.bankconnectors

import code.api.util.APIUtil.{OBPEndpoint, _}
import code.api.util.CallContext
import code.api.util.NewStyle.HttpCode
import code.api.v3_1_0.OBPAPI3_1_0.oauthServe
import com.openbankproject.commons.util.ReflectUtils
import com.openbankproject.commons.util.ReflectUtils.{getType, toValueObject}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.rest.RestHelper
import org.apache.commons.lang3.StringUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.runtime.{universe => ru}

object ConnectorEndpoints extends RestHelper{

  def registerConnectorEndpoints = {
    oauthServe(connectorGetMethod)
  }

  lazy val connectorGetMethod: OBPEndpoint = {
    case "restConnector" :: methodName :: params JsonGet _  if(hashMethod(methodName, params)) => {
      cc => {
        val methodSymbol = getMethod(methodName, params).get
        val optionCC = Option(cc)
        val paramValues: List[Any] = getParamValues(params, methodSymbol.paramLists.headOption.getOrElse(Nil), optionCC)
        val  value = invokeMethod(methodSymbol, paramValues :_*)
        // convert any to Future[(Box[_], Option[CallContext])]  type
        val futureValue: Future[(Box[_], Option[CallContext])] = toStandaredFuture(value)

        for {
          (Full(data), callContext) <- futureValue
          adapterCallContext = callContext.orElse(optionCC).map(_.toAdapterCallContext).orNull
        } yield {
          // NOTE: if any filed type is BigDecimal, it is can't be serialized by lift json
          val json = Map(("adapterCallContext", adapterCallContext),("data", toValueObject(data)))
          (json, HttpCode.`200`(cc))
        }
      }
    }
  }

//  def buildInboundObject(adapterCallContext: AdapterCallContext, data: Any, methodName: String): Any = {
//    val inboundType = getTypeByName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
//    val dataType = inboundType.decl(ru.termNames.CONSTRUCTOR).asMethod.paramLists(0)(1).info
//    val convertedData = toOther[Any](data, dataType)
//    invokeConstructor(inboundType, adapterCallContext, convertedData)
//  }

  def convertValue(str: String, tp: ru.Type): Any = {
    tp match {
      case _ if(tp =:= ru.typeOf[String]) => str
      case _ if(StringUtils.isBlank(str)) => null
      case _ if(tp =:= ru.typeOf[Int]) => str.toInt
      case _ if(tp =:= ru.typeOf[BigDecimal]) => BigDecimal(str)
      case _ if(tp =:= ru.typeOf[Boolean]) => "true" equalsIgnoreCase str
        // have single param constructor case class
      case _ if(tp.typeSymbol.asClass.isCaseClass && tp.decl(ru.termNames.CONSTRUCTOR).asMethod.paramLists.headOption.getOrElse(Nil).size == 1) => {
        ReflectUtils.invokeConstructor(tp) { tps =>
          val value = convertValue(str, tps.head)
          List(value)
        }
      }
      case _ => throw new IllegalAccessException(s"$tp type  is not support in the url, it means Shuang have not supply this type, please contact with Shuang to ask support it")
    }
  }

  def getParamValues(params: List[String], symbols: List[ru.Symbol], optionCC: Option[CallContext]): List[Any] = {
    val paramNameToValue: Map[String, String] = params.grouped(2).map{
      case name::value::Nil => (name.asInstanceOf[String], value)
      case name::Nil => (name.asInstanceOf[String], null)
    }.toMap

    symbols.map {symbol =>
      (symbol.name.toString, symbol.info) match {
        case ("callContext", _) => optionCC
        case(name, tp) => convertValue(paramNameToValue(name),tp)
        case _ => throw new IllegalArgumentException("impossible exception! just a placeholder.")
      }
    }
  }

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val mirrorObj: ru.InstanceMirror = mirror.reflect(LocalMappedConnector)

  // (methodName, paramNames, method)
  lazy val allMethods: List[(String, List[String], ru.MethodSymbol)] = {
     val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
     val objMirror = mirror.reflect(LocalMappedConnector)

     objMirror.symbol.toType.members
       .filter(_.isMethod)
       .map(it => (it.name.toString, it.asMethod.paramLists.headOption.getOrElse(Nil).map(_.name.toString), it.asMethod))
      .toList
  }

  def getMethod(methodName: String, params: List[String]): Option[ru.MethodSymbol] = {
    val paramNames: Seq[String] = (0 until (params.size, 2)).map(params)
    this.allMethods.filter { triple =>
      triple._1 == methodName && triple._2.filterNot("callContext" ==) == paramNames
    }
      .sortBy(_._2.size)
      .lastOption
    .map(_._3)
  }

  def hashMethod(methodName: String, params: List[String]): Boolean = getMethod(methodName, params).isDefined

  def invokeMethod(method: ru.MethodSymbol, args: Any*) = {
    mirrorObj.reflectMethod(method).apply(args :_*)
  }

  def toStandaredFuture(obj: Any): Future[(Box[_], Option[CallContext])] = {
    obj match {
      case null => Future((Empty, None))
      case _: Future[_] => {
        obj.asInstanceOf[Future[_]].map {value =>
          value match {
            case (_, _) => value.asInstanceOf[(Box[_], Option[CallContext])]
            case _ : Box[_] => {
              val boxedValue = value.asInstanceOf[Box[(_, Option[CallContext])]]
              (boxedValue.map(_._1), boxedValue.map(_._2).orElse(None).flatten)
            }
            case _ => throw new IllegalArgumentException(s"not supported type ${getType(value)}")
          }
        }
      }
      case Full(data) => {
        data match {
          case _: (_, _) => toStandaredFuture(Future(obj))
          case _ => {
            val fillCallContext = obj.asInstanceOf[Box[_]].map((_, None))
            toStandaredFuture(Future(fillCallContext))
          }
        }
      }
      case Empty => toStandaredFuture(Future((Empty, None)))
      case _ => {
        val wrappedObj = (Full(obj), None)
        toStandaredFuture(Future(wrappedObj))
      }
    }
  }
}
