package code.bankconnectors

import org.json4s._
import code.api.APIFailureNewStyle
import code.api.util.APIUtil._
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, ApiRole, CallContext, CustomJsonFormats, NewStyle, OBPQueryParam}
import code.bankconnectors.ConnectorEndpoints.getMethod
import code.bankconnectors.rest.RestConnector_vMar2019
import code.util.Helper
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ReflectUtils
import com.openbankproject.commons.util.ReflectUtils.{getType, toValueObject}
import com.openbankproject.commons.util.ConnectorEndpointsTypes
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.github.dwickern.macros.NameOf.nameOf
import org.json4s.JValue
import org.json4s.JsonAST.JNothing
import org.apache.commons.lang3.StringUtils

import scala.annotation.tailrec
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.ManifestFactory
import scala.reflect.runtime.{universe => ru}

object ConnectorEndpoints {

  // Lift dispatch removed in Phase B; not yet migrated to http4s
  def registerConnectorEndpoints = ()

  def extractOBPQueryParams(outBound: AnyRef): Seq[OBPQueryParam] = {
    val tp = ReflectUtils.getType(outBound)
    val decls = tp.decls.toList
    val limit = decls.find(it => it.name.toString == OBPQueryParam.LIMIT).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.toString)
    val offset = decls.find(it => it.name.toString == OBPQueryParam.OFFSET).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.toString)
    val fromDate = decls.find(it => it.name.toString == OBPQueryParam.FROM_DATE).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.asInstanceOf[String])
    val toDate = decls.find(it => it.name.toString == OBPQueryParam.TO_DATE).map(_.asMethod).map(ReflectUtils.invokeMethod(outBound,_)).map(_.asInstanceOf[String])
    List(
      OBPQueryParam.toLimit(limit),
      OBPQueryParam.toOffset(offset),
      OBPQueryParam.toFromDate(fromDate),
      OBPQueryParam.toToDate(toDate)
    ).filter(_.isDefined).map(_.openOrThrowException("Impossible exception!"))
  }

//  def buildInboundObject(adapterCallContext: OutboundAdapterCallContext, data: Any, methodName: String): Any = {
//    val inboundType = getTypeByName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
//    val dataType = inboundType.decl(ru.termNames.CONSTRUCTOR).asMethod.paramLists(0)(1).info
//    val convertedData = toOther[Any](data, dataType)
//    invokeConstructor(inboundType, adapterCallContext, convertedData)
//  }

  def convertValue(str: String, tp: ru.Type): Any = {
    val typeArg = tp.typeArgs.headOption

    tp match {
      case _ if(tp =:= ConnectorEndpointsTypes.tString) => str
      case _ if(StringUtils.isBlank(str)) => null
      case _ if(tp =:= ConnectorEndpointsTypes.tInt) => str.toInt
      case _ if(tp =:= ConnectorEndpointsTypes.tBigDecimal) => BigDecimal(str)
      case _ if(tp =:= ConnectorEndpointsTypes.tBoolean) => "true" equalsIgnoreCase str
      case _ if(tp <:< ConnectorEndpointsTypes.tListWildcard) => str.split(";").map(convertValue(_, typeArg.get)).toList
      case _ if(tp <:< ConnectorEndpointsTypes.tSetWildcard) => str.split(";").map(convertValue(_, typeArg.get)).toSet
      case _ if(tp <:< ConnectorEndpointsTypes.tArrayWildcard) => str.split(";").map(convertValue(_, typeArg.get))
        // have single param constructor case class
      case _ if(tp.typeSymbol.asClass.isCaseClass) => {
        val paramList: Seq[ru.Symbol] = tp.decl(ru.termNames.CONSTRUCTOR).asMethod.paramLists.headOption.getOrElse(Nil)
        val params: Seq[Any] = paramList.size match {
          case 1 => Seq(convertValue(str, paramList.head.info))
          case size if(size > 1) => str.split(",", size).zipAll(paramList, null, null).map(it => convertValue(it._1, it._2.info))
          case _ => throw new IllegalStateException(s"constructor must have at lest one parameter, but $tp constructor have no parameter.")
        }
        ReflectUtils.invokeConstructor(tp, params:_*)
      }
      case _ => throw new IllegalAccessException(s"$tp type  is not support in the url, it means Shuang have not supply this type, please contact with Shuang to ask support it")
    }
  }

  def getParamValues(outBound: AnyRef, methodSymbol: ru.MethodSymbol, optionCC: Option[CallContext]): Seq[Any] = {
    Helper.convertToId(outBound) // convert reference to customerId and accountId
    val paramNameToValue: Map[String, Any] = ReflectUtils.getConstructorArgs(outBound)

    val paramValues: Seq[Any] = allMethods.find(_._3 == methodSymbol)
      .map(_._4).get
      .map(it => it match {
        case "queryParams" => extractOBPQueryParams(outBound)
        case "callContext" => optionCC
        case name => paramNameToValue(name)
      })

      paramValues
  }
  private lazy val connector: Connector = {
    val connectorName = APIUtil.getPropsValue("connector.name.export.as.endpoints", "mapped")
    Connector.getConnectorInstance(connectorName)
  }
  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val mirrorObj: ru.InstanceMirror = mirror.reflect(connector)

  // it is impossible to get the type of OBPQueryParam*, ru.typeOf[OBPQueryParam*] not work, it is Seq type indeed
  // Seq[OBPQueryParam]/Option[CallContext] are obp-api's own types, so they can't be precomputed in
  // obp-commons (SwaggerTypes-style) - build them at runtime from class names instead, same as ConnectorUtils.
  private val paramsType =
    ru.appliedType(
      ReflectUtils.forType("scala.collection.immutable.Seq").typeConstructor,
      ReflectUtils.forType("code.api.util.OBPQueryParam"))
  private val callContextType =
    ru.appliedType(
      ReflectUtils.forType("scala.Option").typeConstructor,
      ReflectUtils.forType("code.api.util.CallContext"))

  // (methodName, paramNames, method, allParamNames, fn: paramName => isOption)
  lazy val allMethods: List[(String, List[String], ru.MethodSymbol, List[String], String => Boolean)] = {
     val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)

     val isCallContextOrQueryParams = (tp: ru.Type) => {
       tp <:< callContextType || tp <:< paramsType
     }
    mirrorObj.symbol.toType.members
       .filter(_.isMethod)
       .map(it => {
         val allParams = it.asMethod.paramLists.headOption.getOrElse(Nil)
         val allNames = allParams.map(_.name.toString.trim)
         // names exclude callContext and queryParams
         val names = allParams
           .filterNot(symbol => isCallContextOrQueryParams(symbol.info))
           .map(_.name.toString.trim)
         val paramNameToIsOption: Map[String, Boolean] = allParams.map(it => (it.name.toString.trim, it.info <:< ConnectorEndpointsTypes.tOptionWildcard)).toMap
         val isParamOption: String => Boolean = name => paramNameToIsOption.get(name).filter(true ==).isDefined
         (it.name.toString, names, it.asMethod, allNames, isParamOption)
       })
      .toList
  }

  def getMethod(methodName: String, json: JValue): Option[ru.MethodSymbol] = {
    this.allMethods.filter { quadruple =>
      val (mName, paramNames, _, _, isParamOption) = quadruple
      mName == methodName && paramNames.forall(paramName => isParamOption(paramName) || (json \ paramName) != JNothing)
    }
      .sortBy(_._2.size)
      .lastOption
      .map(_._3)
  }

  def hashMethod(methodName: String, json: JValue): Boolean = getMethod(methodName, json).isDefined

  def invokeMethod(method: ru.MethodSymbol, args: Any*) = {
    mirrorObj.reflectMethod(method).apply(args :_*)
  }

  @tailrec
  def toStandardFuture(obj: Any): Future[(Box[_], Option[CallContext])] = {
    obj match {
      case null => Future((Empty, None))
      case future: Future[_] => {
        future.map {value =>
          value match {
            case (_, _) => value.asInstanceOf[(Box[_], Option[CallContext])]
            case _ : Box[_] => {
              val boxedValue = value.asInstanceOf[Box[(_, Option[CallContext])]]
              (boxedValue.map(_._1), boxedValue.map(_._2).orElse(None).flatten)
            }
            case _ => throw new IllegalArgumentException(s"not supported type ${getType(value)}")
          }
        }
        .map(Helper.convertToReference(_)) //convert customerId and AccountId to reference
      }
      case Full(data) => {
        data match {
          case _: (_, _) => toStandardFuture(Future(obj))
          case _ => {
            val fillCallContext = obj.asInstanceOf[Box[_]].map((_, None))
            toStandardFuture(Future(fillCallContext))
          }
        }
      }
      case failure: Failure => {
        Future((failure.asInstanceOf[Failure], None))
      }
      case Empty => {
        Future((Full(null), None))
      }
      case _ => Future((Full(obj), None))
    }
  }
}
