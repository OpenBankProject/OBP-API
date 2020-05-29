package code.DynamicEndpoint

import code.api.util.CustomJsonFormats
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedDynamicEndpointProvider extends DynamicEndpointProvider with CustomJsonFormats{
  override def create(swaggerString: String): Box[DynamicEndpointT] = {
    tryo{DynamicEndpoint.create.SwaggerString(swaggerString).saveMe()}
  }
  override def update(dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT] = {
    DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId)).map(_.SwaggerString(swaggerString).saveMe())
  }

  override def get(dynamicEndpointId: String): Box[DynamicEndpointT] = DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))

  override def getAll(): List[DynamicEndpointT] = DynamicEndpoint.findAll()

  override def delete(dynamicEndpointId: String): Boolean = DynamicEndpoint.bulkDelete_!!(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))
 
}

class DynamicEndpoint extends DynamicEndpointT with LongKeyedMapper[DynamicEndpoint] with IdPK {

  override def getSingleton = DynamicEndpoint

  object DynamicEndpointId extends MappedUUID(this)

  object SwaggerString extends MappedText(this)

  override def dynamicEndpointId: Option[String] = Option(DynamicEndpointId.get)
  override def swaggerString: String = SwaggerString.get
}

object DynamicEndpoint extends DynamicEndpoint with LongKeyedMetaMapper[DynamicEndpoint] {
  override def dbIndexes = UniqueIndex(DynamicEndpointId) :: super.dbIndexes
}

