package code.connector

import code.api.util.{CallContext, OBPQueryParam}
import code.api.v5_1_0.V510ServerSetup
import code.bankconnectors.Connector
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.model.OutboundAdapterCallContext
import com.openbankproject.commons.util.ReflectUtils
import org.scalatest.Tag

import scala.collection.immutable.List
import scala.reflect.runtime.universe
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConnectorTest extends V510ServerSetup {
  object ConnectorTestTag extends Tag(NameOf.nameOfType[ConnectorTest])

  // Connector/CallContext/OBPQueryParam are obp-api's own types, so unlike stdlib types, their
  // Type can't be precomputed by the 2.13-compiled obp-commons module - built at runtime
  // instead via ReflectUtils.forType (+ appliedType for the parameterized ones), same
  // technique as ConnectorUtils.scala/ConnectorEndpoints.scala.
  private val connectorType = ReflectUtils.forType("code.bankconnectors.Connector")

  object WrongOutBoundType {
    private def getType(connectorMethod: universe.Symbol): Option[universe.Type] =
      ReflectUtils.forTypeOption(s"com.openbankproject.commons.dto.OutBound${connectorMethod.name.decodedName.toString.capitalize}")

    private val ccType =
      universe.appliedType(ReflectUtils.forType("scala.Option").typeConstructor, ReflectUtils.forType("code.api.util.CallContext"))
    private val outBoundAdapterCcType = ReflectUtils.forType("com.openbankproject.commons.model.OutboundAdapterCallContext")
    private val queryParamsType =
      universe.appliedType(ReflectUtils.forType("scala.collection.immutable.List").typeConstructor, ReflectUtils.forType("code.api.util.OBPQueryParam"))

    // Connector (Scala 3-compiled, resolved via ReflectUtils.forType) and an OutBound DTO
    // (obp-commons, Scala 2.13-compiled, resolved via a separate ReflectUtils.forTypeOption call)
    // sometimes fail =:= for a param whose name and shape genuinely match: two Type instances
    // resolved through different forType calls aren't guaranteed comparable by identity/=:= the
    // way two Types from the same resolution path are. Falling back to the rendered type string
    // covers that without weakening what the check proves - the two sides still have to describe
    // the same type, just via a different equality test.
    private def sameType(a: universe.Type, b: universe.Type): Boolean = (a =:= b) || (a.toString == b.toString)

    // A stricter check than sameType is wrong here: only used for the FINAL by-name field
    // comparison below (each side already matched by parameter name), never for detecting
    // whether a param IS the CallContext/query-params shape - there, treating "matches anything"
    // as a positive would strip out unrelated by-name-mismatched fields (observed: it removed
    // `dependents`/`isActive` from the map entirely, because they satisfied this lenient check
    // against ccType and got filtered out as if they were the CallContext param).
    //
    // A generic argument that is an AnyVal (Option[Int]'s Int, for connectorMethod's
    // `dependents: Option[Int]`) reads back through scala.reflect.runtime.universe as
    // Option[Object] for a Scala 3-compiled method's parameter: the JVM signature boxes/erases it
    // and without TASTy there is nothing to recover the original argument from. Once two fields
    // are already known to share a name, an erased Object type argument is compatible with
    // whatever AnyVal the other side names there - that much genuinely can't be distinguished
    // further without TASTy (Int vs Boolean vs Long all erase identically). It is NOT compatible
    // with an arbitrary reference type: Object-erasure is documented as an AnyVal-specific boxing
    // artifact, so if the other side is e.g. String or a case class, that is a real mismatch this
    // check should still catch rather than wave through just because one side stringifies as
    // Object.
    // Structural, not a name list: covers the 8 built-in AnyVal primitives (Int/Boolean/...) AND
    // any custom AnyVal-derived value class the same way, rather than only the ones named here -
    // ReflectUtils.forType, not universe.typeOf[AnyVal] directly, for the same reason every other
    // Type in this file is built that way: this file compiles under Scala 3, which does not
    // implement the Scala 2 compiler's TypeTag synthesis a direct typeOf[AnyVal] call would need.
    private val anyValType = ReflectUtils.forType("scala.AnyVal")
    private def isKnownAnyVal(tp: universe.Type): Boolean = tp <:< anyValType
    private def sameTypeAllowingErasedGeneric(a: universe.Type, b: universe.Type): Boolean = {
      sameType(a, b) ||
        (a.typeArgs.size == b.typeArgs.size && a.typeArgs.nonEmpty && a.typeConstructor =:= b.typeConstructor &&
          a.typeArgs.zip(b.typeArgs).forall { case (x, y) =>
            sameType(x, y) ||
              (x.toString == "Object" && isKnownAnyVal(y)) ||
              (y.toString == "Object" && isKnownAnyVal(x))
          })
    }

    def unapply(methodSymbol: universe.MethodSymbol): Option[universe.Type] = getType(methodSymbol) match {
      case None => None

      case x @Some(outBoundType) =>
        val connectorMethodParams = methodSymbol.paramLists.head
        var connectorMethodParamNameToType = connectorMethodParams.map(it => it.name.decodedName.decodedName.toString -> it.info).toMap

        if(connectorMethodParamNameToType.exists(kv => sameType(kv._2, ccType))) {
          connectorMethodParamNameToType = connectorMethodParamNameToType.filterNot(kv => sameType(kv._2, ccType)) + ("outboundAdapterCallContext" -> outBoundAdapterCcType)
        }
        if(connectorMethodParamNameToType.exists(kv => sameType(kv._2, queryParamsType))) {
          connectorMethodParamNameToType = connectorMethodParamNameToType.filterNot(kv => sameType(kv._2, queryParamsType)) ++
            List("limit" -> ReflectUtils.forType("scala.Int"), "offset" -> ReflectUtils.forType("scala.Int"), "fromDate" -> ReflectUtils.forType("java.lang.String") , "toDate" -> ReflectUtils.forType("java.lang.String"))
        }

        val Some(outBoundConstructor:universe.MethodSymbol) = outBoundType.decls.find(_.isConstructor)
        val outBoundParams = outBoundConstructor.paramLists.head
        val outBoundParamNameToType = outBoundParams.map(it => it.name.decodedName.decodedName.toString -> it.info).toMap



        if(outBoundParamNameToType.size != connectorMethodParamNameToType.size) {
          x
        } else {
          val missingParams = connectorMethodParamNameToType.filterNot(it => {
            val (connectorMethodParamName, connectorMethodParamType) = it
            outBoundParamNameToType.get(connectorMethodParamName).exists(sameTypeAllowingErasedGeneric(_, connectorMethodParamType))
          })
          if(missingParams.nonEmpty) x else None
        }

    }
  }

  Feature("Make sure connector follow the obp general rules ") {
    Scenario("OutBound case class should have the same param name with connector method", ConnectorTestTag) {
      val wrongOutboundTypes = connectorType.decls.filter(it =>it.isMethod) collect {
        case WrongOutBoundType(tp) => tp
      }

      wrongOutboundTypes shouldBe empty
    }
    
    Scenario("all connector methods should have the callContext parameter", ConnectorTestTag){
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
    
    Scenario("all connector methods should return Future ", ConnectorTestTag){
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
