package code.authtypevalidation

/* For CardAttribute */

import code.api.util.AuthType
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json.JsonDSL._
import net.liftweb.json
import net.liftweb.json.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector
import org.apache.commons.lang3.StringUtils

object AuthTypeValidationProvider extends SimpleInjector {

  val validationProvider = new Inject(buildOne _) {}

  def buildOne: MappedAuthTypeValidationProvider.type = MappedAuthTypeValidationProvider
}

case class JsonAuthTypeValidation(operationId: String, authTypes: List[AuthType]) extends JsonAble {

  override def toJValue(implicit format: Formats): JsonAST.JValue =
    ("operation_id", operationId) ~ ("allowed_auth_types", json.Extraction.decompose(authTypes.map(_.toString)))
}

object JsonAuthTypeValidation {
  def apply(operationId: String, authTypes: String): JsonAuthTypeValidation = {
    val typeList = StringUtils.split(authTypes, ",").toList.map(AuthType.withName)
    JsonAuthTypeValidation(operationId, typeList)
  }
}

trait AuthTypeValidationProvider {

  def getByOperationId(operationId: String): Box[JsonAuthTypeValidation]

  def getAll(): List[JsonAuthTypeValidation]

  def create(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation]
  def update(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation]
  def deleteByOperationId(operationId: String): Box[Boolean]

}
