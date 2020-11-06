package code.DynamicEndpoint

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object DynamicEndpointProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicEndpointProvider.type = MappedDynamicEndpointProvider
}

trait DynamicEndpointT {
  def dynamicEndpointId: Option[String]
  def swaggerString: String
  /**
   * The user who create this DynamicEndpoint
   */
  def userId: String
}

case class DynamicEndpointCommons(
                                dynamicEndpointId: Option[String] = None,
                                swaggerString: String,
                                userId: String
                               ) extends DynamicEndpointT with JsonFieldReName

object DynamicEndpointCommons extends Converter[DynamicEndpointT, DynamicEndpointCommons]

case class DynamicEndpointSwagger(swaggerString: String, dynamicEndpointId: Option[String] = None)

trait DynamicEndpointProvider {
  def create(userId: String, swaggerString: String): Box[DynamicEndpointT]
  def update(dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT]
  def get(dynamicEndpointId: String): Box[DynamicEndpointT]
  def getAll(): List[DynamicEndpointT]
  def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT]
  def delete(dynamicEndpointId: String): Boolean
}