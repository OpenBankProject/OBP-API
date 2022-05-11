package code.DynamicEndpoint

import java.util.UUID.randomUUID
import code.api.cache.Caching
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.MappedUUID
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.JString
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

object MappedDynamicEndpointProvider extends DynamicEndpointProvider with CustomJsonFormats{
  val dynamicEndpointTTL : Int = {
    if(Props.testMode) 0
    else //Better set this to 0, we maybe create multiple endpoints, when we create new ones. 
      APIUtil.getPropsValue(s"dynamicEndpoint.cache.ttl.seconds", "0").toInt
  }

  override def create(bankId:Option[String], userId: String, swaggerString: String): Box[DynamicEndpointT] = {
    tryo{DynamicEndpoint.create
      .UserId(userId)
      .BankId(bankId.getOrElse(null))
      .SwaggerString(swaggerString)
      .saveMe()
    }
  }
  override def update(bankId:Option[String], dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT] = {
    (if (bankId.isEmpty)
      DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))
    else
      DynamicEndpoint.find(
        By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId),
        By(DynamicEndpoint.BankId, bankId.getOrElse(""))
      )
    ).map(_.SwaggerString(swaggerString).saveMe())
        
    
  }
  override def updateHost(bankId: Option[String], dynamicEndpointId: String, hostString: String): Box[DynamicEndpointT] = {
    (if (bankId.isEmpty)
      DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))
     else   
      DynamicEndpoint.find(
        By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId),
        By(DynamicEndpoint.BankId, bankId.getOrElse(""))
      )
    ).map(dynamicEndpoint => {
        val updatedHost = DynamicEndpointHelper.changeOpenApiVersionHost(dynamicEndpoint.swaggerString, hostString )
        dynamicEndpoint.SwaggerString(updatedHost).saveMe()
      }
      )
  }

  override def get(bankId: Option[String], dynamicEndpointId: String): Box[DynamicEndpointT] = {
    if (bankId.isEmpty)
      DynamicEndpoint.find(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))
    else
      DynamicEndpoint.find(
        By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId),
        By(DynamicEndpoint.BankId, bankId.getOrElse(""))
      )
    
  }

  override def getAll(bankId: Option[String]): List[DynamicEndpointT] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (dynamicEndpointTTL second) {
        if (bankId.isEmpty)
          DynamicEndpoint.findAll()
        else
          DynamicEndpoint.findAll(By(DynamicEndpoint.BankId, bankId.getOrElse("")))
      }
    }
  }
  
  override def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT] = DynamicEndpoint.findAll(By(DynamicEndpoint.UserId, userId))

  override def delete(bankId: Option[String], dynamicEndpointId: String): Boolean = {
    if (bankId.isEmpty)
      DynamicEndpoint.bulkDelete_!!(By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId))
    else
      DynamicEndpoint.bulkDelete_!!(
        By(DynamicEndpoint.DynamicEndpointId, dynamicEndpointId),
        By(DynamicEndpoint.BankId, bankId.getOrElse(""))
      )
  }
 
}

class DynamicEndpoint extends DynamicEndpointT with LongKeyedMapper[DynamicEndpoint] with IdPK with CreatedUpdated {

  override def getSingleton = DynamicEndpoint

  object DynamicEndpointId extends MappedUUID(this)

  object SwaggerString extends MappedText(this)
  
  object UserId extends MappedString(this, 255)
  
  object BankId extends MappedString(this, 255)

  override def dynamicEndpointId: Option[String] = Option(DynamicEndpointId.get)
  override def swaggerString: String = SwaggerString.get
  override def userId: String = UserId.get
  override def bankId: Option[String] = if (BankId.get == null || BankId.get.isEmpty) None else Some(BankId.get)
}

object DynamicEndpoint extends DynamicEndpoint with LongKeyedMetaMapper[DynamicEndpoint] {
  override def dbIndexes = UniqueIndex(DynamicEndpointId) :: super.dbIndexes
}

