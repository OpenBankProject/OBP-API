package code.connector

import code.api.util.{CallContext, OBPQueryParam}
import code.bankconnectors.Connector
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.model.OutboundAdapterCallContext
import com.openbankproject.commons.util.ReflectUtils
import org.scalatest.{FlatSpec, Matchers, Tag}

import scala.collection.immutable.List
import scala.reflect.runtime.universe

class ConnectorTest extends FlatSpec with Matchers {
  object ConnectorTestTag extends Tag(NameOf.nameOfType[ConnectorTest])

  private val connectorType = universe.typeOf[Connector]

  object WrongOutBoundType {
    private def getType(connectorMethod: universe.Symbol): Option[universe.Type] =
      ReflectUtils.forTypeOption(s"com.openbankproject.commons.dto.OutBound${connectorMethod.name.decodedName.toString.capitalize}")

    private val ccType = universe.typeOf[Option[CallContext]]
    private val outBoundAdapterCcType = universe.typeOf[OutboundAdapterCallContext]
    private val queryParamsType = universe.typeOf[List[OBPQueryParam]]

    def unapply(methodSymbol: universe.MethodSymbol): Option[universe.Type] = getType(methodSymbol) match {
      case None => None

      case x @Some(outBoundType) =>
        val connectorMethodParams = methodSymbol.paramLists.head
        var connectorMethodParamNameToType = connectorMethodParams.map(it => it.name.decodedName.decodedName.toString -> it.info).toMap

        if(connectorMethodParamNameToType.exists(_._2 =:= ccType)) {
          connectorMethodParamNameToType = connectorMethodParamNameToType.filterNot(_._2 =:= ccType) + ("outboundAdapterCallContext" -> outBoundAdapterCcType)
        }
        if(connectorMethodParamNameToType.exists(_._2 =:= queryParamsType)) {
          connectorMethodParamNameToType = connectorMethodParamNameToType.filterNot(_._2 =:= queryParamsType) ++
            List("limit" -> universe.typeOf[Int], "offset" -> universe.typeOf[Int], "fromDate" -> universe.typeOf[String] , "toDate" -> universe.typeOf[String])
        }

        val Some(outBoundConstructor:universe.MethodSymbol) = outBoundType.decls.find(_.isConstructor)
        val outBoundParams = outBoundConstructor.paramLists.head
        val outBoundParamNameToType = outBoundParams.map(it => it.name.decodedName.decodedName.toString -> it.info).toMap



        if(outBoundParamNameToType.size != connectorMethodParamNameToType.size) {
          x
        } else {
          val missingParams = connectorMethodParamNameToType.filterNot(it => {
            val (connectorMethodParamName, connectorMethodParamType) = it
            outBoundParamNameToType.get(connectorMethodParamName).exists(_ =:= connectorMethodParamType)
          })
          if(missingParams.nonEmpty) x else None
        }

    }
  }

  "OutBound case class" should "have the same param name with connector method" taggedAs ConnectorTestTag in {

    val wrongOutboundTypes = connectorType.decls.filter(it =>it.isMethod) collect {
      case WrongOutBoundType(tp) => tp
    }

    wrongOutboundTypes shouldBe empty
  }
}
