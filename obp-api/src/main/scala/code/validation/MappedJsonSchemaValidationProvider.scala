package code.validation

import java.util.UUID.randomUUID
import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

object MappedJsonSchemaValidationProvider extends JsonSchemaValidationProvider {
  val getValidationByOperationIdTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"validation.cache.ttl.seconds", "34").toInt
  }

  override def getByOperationId(operationId: String): Box[JsonValidation] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getValidationByOperationIdTTL second) {
        JsonSchemaValidation.find(By(JsonSchemaValidation.OperationId, operationId))
          .map(it => JsonValidation(it.operationId, it.jsonSchema))
      }}
  }

  override def getAll(): List[JsonValidation] = JsonSchemaValidation.findAll()
    .map(it => JsonValidation(it.operationId, it.jsonSchema))

  override def create(jsonValidation: JsonValidation): Box[JsonValidation] =
    tryo {
      JsonSchemaValidation.create
      .OperationId(jsonValidation.operationId)
      .JsonSchema(jsonValidation.jsonSchema)
      .saveMe()
    }.map(it => JsonValidation(it.operationId, it.jsonSchema))


  override def update(jsonValidation: JsonValidation): Box[JsonValidation] = {
    JsonSchemaValidation.find(By(JsonSchemaValidation.OperationId, jsonValidation.operationId)) match {
      case Full(v) =>
        tryo {
          v.JsonSchema(jsonValidation.jsonSchema).saveMe()
        }.map(it => JsonValidation(it.operationId, it.jsonSchema))
      case _ => Empty
    }
  }

  override def deleteByOperationId(operationId: String): Box[Boolean] = tryo {
    JsonSchemaValidation.bulkDelete_!!(By(JsonSchemaValidation.OperationId, operationId))
  }
}


