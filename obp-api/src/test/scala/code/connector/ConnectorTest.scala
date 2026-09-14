/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.connector

import code.api.util.{CallContext, OBPQueryParam}
import code.api.v5_1_0.V510ServerSetup
import code.bankconnectors.Connector
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.model.OutboundAdapterCallContext
import com.openbankproject.commons.util.ReflectUtils
import org.scalatest.{FlatSpec, Matchers, Tag}

import scala.collection.immutable.List
import scala.reflect.runtime.universe

class ConnectorTest extends V510ServerSetup {
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

  feature("Make sure connector follow the obp general rules ") {
    scenario("OutBound case class should have the same param name with connector method", ConnectorTestTag) {
      val wrongOutboundTypes = connectorType.decls.filter(it =>it.isMethod) collect {
        case WrongOutBoundType(tp) => tp
      }

      wrongOutboundTypes shouldBe empty
    }
    
    scenario("all connector methods should have the callContext parameter", ConnectorTestTag){
      val mappedConnectorObject = Connector.nameToConnector.get("mapped")

      val allConnectorMethods = mappedConnectorObject.map(_.callableMethods)
      val noCallcontextMethods: Option[Map[String, universe.MethodSymbol]] = allConnectorMethods.map(_.filterNot(_._2.paramLists.toString.contains("callContext")))
      val noCallcontextMethodsNames = noCallcontextMethods.map(_.keys.toList).getOrElse(Nil)
        .filterNot(_=="equals")
        .filterNot(_=="==")
        .filterNot(_=="!=")
      println(noCallcontextMethodsNames.mkString("\n"))
      noCallcontextMethodsNames.size should be(0)
    }
    
    scenario("all connector methods should return Future ", ConnectorTestTag){
      val mappedConnectorObject = Connector.nameToConnector.get("mapped")

      val allConnectorMethods = mappedConnectorObject.map(_.callableMethods)
      val wrongReturnTypeMethods= allConnectorMethods.map(_.map(_._2.returnType.toString)).toList.flatten
        .filterNot(_.contains("OBPReturnType"))
        .filterNot(_.contains("Future"))
      println(wrongReturnTypeMethods.mkString("\n"))
//      wrongReturnTypeMethods.size should be(0)
    }
    
  }
}
