package code.methodrouting

/* For Connector method routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JValue}
import net.liftweb.json.JsonAST.{JArray, JBool, JField, JNull, JObject, JString}
import net.liftweb.util.SimpleInjector

object MethodRoutingProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedMethodRoutingProvider.type = MappedMethodRoutingProvider
}

trait MethodRoutingT {
  def methodRoutingId: Option[String]
  def methodName: String
  def bankIdPattern: Option[String]

  /**
    * whether bankIdPattern is exact match the bankId value, or regex expression match
    * @return true if exact match, false if regex match
    */
  def isBankIdExactMatch: Boolean
  def connectorName: String
  def parameters: List[MethodRoutingParam]

  def getInBoundMapping: Option[JObject] = parameters.find(_.key == "inBoundMapping")
    .map(it => json.parse(it.value).asInstanceOf[JObject])

  def getOutBoundMapping: Option[JObject] = parameters.find(_.key == "outBoundMapping")
    .map(it => json.parse(it.value).asInstanceOf[JObject])
}

case class MethodRoutingCommons(methodName: String,
                                connectorName: String,
                                isBankIdExactMatch: Boolean,
                                bankIdPattern: Option[String],
                                parameters: List[MethodRoutingParam] = Nil,
                                methodRoutingId: Option[String] = None,
                               ) extends MethodRoutingT with JsonFieldReName {
  /**
    * when serialized to json, the  Option field will be not shown, this method just generate a full fields json, include all None value fields
    * @return JObject include all fields
    */
  def toJson(implicit format: Formats) = {
    val paramsJson: List[JValue] = this.parameters.map(_.toJValue)

    JObject(List(
      JField("method_name", JString(this.methodName)),
      JField("connector_name", JString(this.connectorName)),
      JField("is_bank_id_exact_match", JBool(this.isBankIdExactMatch)),
      JField("bank_id_pattern", this.bankIdPattern.map(JString(_)).getOrElse(JString("*"))),
      JField("parameters", JArray(paramsJson)),
      JField("method_routing_id", this.methodRoutingId.map(JString(_)).getOrElse(JNull))
    ))
  }
}

object MethodRoutingCommons extends Converter[MethodRoutingT, MethodRoutingCommons]

case class MethodRoutingParam(key: String, value: String) extends JsonAble {
  def this(jObject: JObject) = this(MethodRoutingParam.extractKey(jObject),MethodRoutingParam.extractValue(jObject))

  override def toJValue(implicit format: Formats): JValue =
    ("key" -> key) ~
    ("value" -> {
      val trimmedValue = value.trim
      if(trimmedValue.startsWith("{") && trimmedValue.endsWith("}")) {
        json.parse(value)
      } else {
        JString(value)
      }
    })
}

object MethodRoutingParam {

  def apply(jValue: JValue) = new MethodRoutingParam(jValue.asInstanceOf[JObject])

  private def extractKey(jObject: JObject): String = {
    (jObject \ "key").asInstanceOf[JString].s
  }
  private def extractValue(jObject: JObject): String = {
    (jObject \ "value") match {
      case  JString(v)  =>  v
      case  obj => json.compactRender(obj)
    }
  }
}

trait MethodRoutingProvider {
  def getById(methodRoutingId: String): Box[MethodRoutingT]

  def getMethodRoutings(methodName: Option[String], isBankIdExactMatch: Option[Boolean] = None, bankIdPattern: Option[String] = None): List[MethodRoutingT]

  def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT]

  def delete(methodRoutingId: String):Box[Boolean]
}






