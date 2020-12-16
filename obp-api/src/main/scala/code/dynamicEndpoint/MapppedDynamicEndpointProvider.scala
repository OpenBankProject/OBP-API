package code.DynamicEndpoint

import java.util.UUID.randomUUID
import code.api.cache.Caching
import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.MappedUUID
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

object MappedDynamicEndpointProvider extends DynamicEndpointProvider with CustomJsonFormats{
  val dynamicEndpointTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"dynamicEndpoint.cache.ttl.seconds", "32").toInt
  }

  override def create(userId: String, swaggerString: String): Box[DynamicEndpointT] = {
    tryo{DynamicEndpoint.create.UserId(userId).SwaggerString(swaggerString).saveMe()}
  }
  override def update(dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT] = {
    DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId)).map(_.SwaggerString(swaggerString).saveMe())
  }

  override def get(dynamicEndpointId: String): Box[DynamicEndpointT] = DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))

  override def getAll(): List[DynamicEndpointT] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (dynamicEndpointTTL second) {
        DynamicEndpoint.findAll()
      }}
  }
  
  override def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT] = DynamicEndpoint.findAll(By(DynamicEndpoint.UserId, userId))

  override def delete(dynamicEndpointId: String): Boolean = DynamicEndpoint.bulkDelete_!!(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))
 
}

class DynamicEndpoint extends DynamicEndpointT with LongKeyedMapper[DynamicEndpoint] with IdPK with CreatedUpdated {

  override def getSingleton = DynamicEndpoint

  object DynamicEndpointId extends MappedUUID(this)

  object SwaggerString extends MappedText(this)
  
  object UserId extends MappedString(this, 255)

  override def dynamicEndpointId: Option[String] = Option(DynamicEndpointId.get)
  override def swaggerString: String = SwaggerString.get
  override def userId: String = UserId.get
}

object DynamicEndpoint extends DynamicEndpoint with LongKeyedMetaMapper[DynamicEndpoint] {
  override def dbIndexes = UniqueIndex(DynamicEndpointId) :: super.dbIndexes
}

