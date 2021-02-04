package code.connectormethod

import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object MappedConnectorMethodProvider extends ConnectorMethodProvider {

  private val getConnectorMethodTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"connectorMethod.cache.ttl.seconds", "40").toInt
  }

  override def getById(connectorMethodId: String): Box[JsonConnectorMethod] = ConnectorMethod
    .find(By(ConnectorMethod.ConnectorMethodId, connectorMethodId))
    .map(it => JsonConnectorMethod(Some(it.ConnectorMethodId.get), it.MethodName.get, it.MethodBody.get))

  override def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod] = {
    ConnectorMethod.find(By(ConnectorMethod.MethodName, methodName))
      .map(it => JsonConnectorMethod(Some(it.ConnectorMethodId.get), it.MethodName.get, it.MethodBody.get))
  }
  
  override def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getConnectorMethodTTL second) {
        getByMethodNameWithoutCache(methodName)
      }}
  }
  override def getAll(): List[JsonConnectorMethod] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getConnectorMethodTTL second) {
        ConnectorMethod.findAll()
          .map(it => JsonConnectorMethod(Some(it.ConnectorMethodId.get), it.MethodName.get, it.MethodBody.get))
      }}
  }

  override def create(entity: JsonConnectorMethod): Box[JsonConnectorMethod]=
    tryo {
      ConnectorMethod.create
      .ConnectorMethodId(APIUtil.generateUUID())
      .MethodName(entity.methodName)
      .MethodBody(entity.methodBody)
      .saveMe()
    }.map(it => JsonConnectorMethod(Some(it.ConnectorMethodId.get), it.MethodName.get, it.MethodBody.get))


  override def update(connectorMethodId: String, connectorMethodBody: String): Box[JsonConnectorMethod] = {
    ConnectorMethod.find(By(ConnectorMethod.ConnectorMethodId, connectorMethodId)) match {
      case Full(v) =>
        tryo {
          v.MethodBody(connectorMethodBody).saveMe()
        }.map(it => JsonConnectorMethod(Some(connectorMethodId), it.MethodName.get, it.MethodBody.get))
      case _ => Empty
    }
  }

  override def deleteById(id: String): Box[Boolean] = tryo {
    ConnectorMethod.bulkDelete_!!(By(ConnectorMethod.ConnectorMethodId, id))
  }
}


