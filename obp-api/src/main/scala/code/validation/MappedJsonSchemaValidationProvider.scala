/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getValidationByOperationIdTTL.second) {
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


