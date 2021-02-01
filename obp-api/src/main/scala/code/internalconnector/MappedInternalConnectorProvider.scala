package code.internalconnector

import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object MappedAuthTypeValidationProvider extends InternalConnectorProvider {

  private val getInternalConnectorTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"internalConnector.cache.ttl.seconds", "40").toInt
  }

  override def getById(internalConnectorId: String): Box[JsonInternalConnector] = InternalConnector
    .find(By(InternalConnector.InternalConnectorId, internalConnectorId))
    .map(it => JsonInternalConnector(it.InternalConnectorId.get, it.MethodName.get, it.MethodBody.get))

  override def getByMethodName(methodName: String): Box[JsonInternalConnector] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getInternalConnectorTTL second) {
        InternalConnector.find(By(InternalConnector.MethodName, methodName))
          .map(it => JsonInternalConnector(it.InternalConnectorId.get, it.MethodName.get, it.MethodBody.get))
      }}
  }
  override def getAll(): List[JsonInternalConnector] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getInternalConnectorTTL second) {
        InternalConnector.findAll()
          .map(it => JsonInternalConnector(it.InternalConnectorId.get, it.MethodName.get, it.MethodBody.get))
      }}
  }

  override def create(entity: JsonInternalConnector): Box[JsonInternalConnector]=
    tryo {
      InternalConnector.create
      .InternalConnectorId(entity.internalConnectorId)
      .MethodName(entity.methodName)
      .MethodBody(entity.methodBody)
      .saveMe()
    }.map(it => JsonInternalConnector(it.InternalConnectorId.get, it.MethodName.get, it.MethodBody.get))


  override def update(entity: JsonInternalConnector): Box[JsonInternalConnector] = {
    InternalConnector.find(By(InternalConnector.InternalConnectorId, entity.internalConnectorId)) match {
      case Full(v) =>
        tryo {
          v.MethodName(entity.methodName)
            .MethodBody(entity.methodBody)
            .saveMe()
        }.map(it => JsonInternalConnector(it.InternalConnectorId.get, it.MethodName.get, it.MethodBody.get))
      case _ => Empty
    }
  }

  override def deleteById(id: String): Box[Boolean] = tryo {
    InternalConnector.bulkDelete_!!(By(InternalConnector.InternalConnectorId, id))
  }
}


