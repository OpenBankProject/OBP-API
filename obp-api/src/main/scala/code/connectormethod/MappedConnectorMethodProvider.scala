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
    .map(ConnectorMethod.getJsonConnectorMethod)

  override def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod] = {
    ConnectorMethod.find(By(ConnectorMethod.MethodName, methodName))
      .map(ConnectorMethod.getJsonConnectorMethod)
  }

  override def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getConnectorMethodTTL.second) {
        getByMethodNameWithoutCache(methodName)
      }}
  }
  override def getAll(): List[JsonConnectorMethod] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getConnectorMethodTTL.second) {
        ConnectorMethod.findAll()
          .map(ConnectorMethod.getJsonConnectorMethod)
      }}
  }

  override def create(entity: JsonConnectorMethod, createdByUserId: Option[String]): Box[JsonConnectorMethod]=
    tryo {
      ConnectorMethod.create
      .ConnectorMethodId(APIUtil.generateUUID())
      .MethodName(entity.methodName)
      .MethodBody(entity.methodBody)
      .Lang(entity.programmingLang)
      // provenance is set here from the authenticated user + computed hash, not from `entity`
      .CreatedByUserId(createdByUserId.getOrElse(null))
      .MethodBodyHash(APIUtil.sha256Hex(entity.decodedMethodBody))
      .saveMe()
    }.map(ConnectorMethod.getJsonConnectorMethod)


  override def update(connectorMethodId: String, connectorMethodBody: String, programmingLang: String, updatedByUserId: Option[String]): Box[JsonConnectorMethod] = {
    ConnectorMethod.find(By(ConnectorMethod.ConnectorMethodId, connectorMethodId)) match {
      case Full(v) =>
        tryo {
          v.MethodBody(connectorMethodBody)
            .Lang(programmingLang)
            // CreatedByUserId is left untouched; record who last changed the code + refresh the hash
            .UpdatedByUserId(updatedByUserId.getOrElse(null))
            .MethodBodyHash(APIUtil.sha256Hex(java.net.URLDecoder.decode(connectorMethodBody, "UTF-8")))
            .saveMe()
        }.map(ConnectorMethod.getJsonConnectorMethod)
      case _ => Empty
    }
  }

  override def deleteById(id: String): Box[Boolean] = tryo {
    ConnectorMethod.bulkDelete_!!(By(ConnectorMethod.ConnectorMethodId, id))
  }
}


