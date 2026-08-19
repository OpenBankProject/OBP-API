package code.DynamicEndpoint

import com.openbankproject.commons.util.ReflectUtils

import com.openbankproject.commons.model.{Converter, ConverterWithType, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object DynamicEndpointProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(() => buildOne) {}

  def buildOne: MappedDynamicEndpointProvider.type = MappedDynamicEndpointProvider
}

trait DynamicEndpointT {
  def dynamicEndpointId: Option[String]
  def swaggerString: String
  /**
   * The user who create this DynamicEndpoint
   */
  def userId: String
  def bankId: Option[String]
}

case class DynamicEndpointCommons(
                                dynamicEndpointId: Option[String] = None,
                                swaggerString: String,
                                userId: String,
                                bankId: Option[String]
                               ) extends DynamicEndpointT with JsonFieldReName

object DynamicEndpointCommons extends ConverterWithType[DynamicEndpointT, DynamicEndpointCommons](ReflectUtils.forType("code.DynamicEndpoint.DynamicEndpointCommons"))

case class DynamicEndpointSwagger(swaggerString: String, dynamicEndpointId: Option[String] = None)

trait DynamicEndpointProvider {
  def create(bankId:Option[String], userId: String, swaggerString: String): Box[DynamicEndpointT]
  def update(bankId:Option[String], dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT]
  def updateHost(bankId:Option[String], dynamicEndpointId: String, hostString: String): Box[DynamicEndpointT]
  def get(bankId:Option[String],dynamicEndpointId: String): Box[DynamicEndpointT]
  def getAll(bankId:Option[String]): List[DynamicEndpointT]
  def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT]
  def delete(bankId:Option[String], dynamicEndpointId: String): Boolean
}