package code.authtypevalidation

/* For CardAttribute */

import org.json4s._
import code.api.util.AuthenticationType
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import org.json4s.JsonDSL._
import com.openbankproject.commons.util.json
import org.json4s.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector
import org.apache.commons.lang3.StringUtils

object AuthenticationTypeValidationProvider extends SimpleInjector {

  val validationProvider = new Inject(buildOne _) {}

  def buildOne: MappedAuthTypeValidationProvider.type = MappedAuthTypeValidationProvider
}

case class JsonAuthTypeValidation(operationId: String, authTypes: List[AuthenticationType]) extends JsonAble {

  override def toJValue(implicit format: Formats): JsonAST.JValue =
    ("operation_id", operationId) ~ ("allowed_authentication_types", json.Extraction.decompose(authTypes.map(_.toString)))
}

object JsonAuthTypeValidation {
  def apply(operationId: String, authTypes: String): JsonAuthTypeValidation = {
    val typeList = StringUtils.split(authTypes, ",").toList.map(AuthenticationType.withName)
    JsonAuthTypeValidation(operationId, typeList)
  }
}

trait AuthenticationTypeValidationProvider {

  def getByOperationId(operationId: String): Box[JsonAuthTypeValidation]

  def getAll(): List[JsonAuthTypeValidation]

  def create(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation]
  def update(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation]
  def deleteByOperationId(operationId: String): Box[Boolean]

}
