package code.authtypevalidation

import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object MappedAuthTypeValidationProvider extends AuthTypeValidationProvider {
  val getValidationByOperationIdTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"authTypeValidation.cache.ttl.seconds", "36").toInt
  }



  override def getByOperationId(operationId: String): Box[JsonAuthTypeValidation] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getValidationByOperationIdTTL second) {
        AuthTypeValidation.find(By(AuthTypeValidation.OperationId, operationId))
          .map(it => JsonAuthTypeValidation(it.operationId, it.allowedAuthTypes))
      }}
  }

  override def getAll(): List[JsonAuthTypeValidation] = AuthTypeValidation.findAll()
    .map(it => JsonAuthTypeValidation(it.operationId, it.allowedAuthTypes))

  override def create(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation] =
    tryo {
      AuthTypeValidation.create
      .OperationId(jsonValidation.operationId)
      .AllowedAuthTypes(jsonValidation.authTypes.mkString(","))
      .saveMe()
    }.map(it => JsonAuthTypeValidation(it.operationId, it.allowedAuthTypes))


  override def update(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation] = {
    AuthTypeValidation.find(By(AuthTypeValidation.OperationId, jsonValidation.operationId)) match {
      case Full(v) =>
        tryo {
          v.AllowedAuthTypes(jsonValidation.authTypes.mkString(",")).saveMe()
        }.map(it => JsonAuthTypeValidation(it.operationId, it.allowedAuthTypes))
      case _ => Empty
    }
  }

  override def deleteByOperationId(operationId: String): Box[Boolean] = tryo {
    AuthTypeValidation.bulkDelete_!!(By(AuthTypeValidation.OperationId, operationId))
  }
}


