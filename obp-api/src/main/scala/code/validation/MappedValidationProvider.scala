package code.validation

import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.duration.DurationInt

object MappedValidationProvider extends ValidationProvider {
  val getValidationByOperationIdTTL : Int = APIUtil.getPropsValue(s"validation.cache.ttl.seconds", "30").toInt

  override def getByOperationId(operationId: String): Box[JsonValidation] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getValidationByOperationIdTTL second) {
        Validation.find(By(Validation.OperationId, operationId))
          .map(it => JsonValidation(it.operationId, it.jsonSchema))
      }}
  }

  override def getAll(): List[JsonValidation] = Validation.findAll()
    .map(it => JsonValidation(it.operationId, it.jsonSchema))

  override def create(jsonValidation: JsonValidation): Box[JsonValidation] =
    tryo {
      Validation.create
      .OperationId(jsonValidation.operationId)
      .JsonSchema(jsonValidation.jsonSchema)
      .saveMe()
    }.map(it => JsonValidation(it.operationId, it.jsonSchema))


  override def update(jsonValidation: JsonValidation): Box[JsonValidation] = {
    Validation.find(By(Validation.OperationId, jsonValidation.operationId)) match {
      case Full(v) =>
        tryo {
          v.JsonSchema(jsonValidation.jsonSchema).saveMe()
        }.map(it => JsonValidation(it.operationId, it.jsonSchema))
      case _ => Empty
    }
  }

  override def deleteByOperationId(operationId: String): Box[Boolean] = tryo {
    Validation.bulkDelete_!!(By(Validation.OperationId, operationId))
  }
}


