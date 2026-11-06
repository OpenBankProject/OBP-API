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

package code.dynamicResourceDoc

import code.api.cache.Caching
import code.api.util.APIUtil
import com.openbankproject.commons.util.json
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

object MappedDynamicResourceDocProvider extends DynamicResourceDocProvider {

  private val getDynamicResourceDocTTL : Int = {
    if(Props.testMode) 0 //make the scala test work
    else APIUtil.getPropsValue(s"dynamicResourceDoc.cache.ttl.seconds", "40").toInt
  }

  override def getById(bankId: Option[String], dynamicResourceDocId: String): Box[JsonDynamicResourceDoc] =
    DynamicResourceDoc.findById(bankId, dynamicResourceDocId)
      .map(DynamicResourceDoc.getJsonDynamicResourceDoc)

  override def getByVerbAndUrl(bankId: Option[String], requestVerb: String,
                               requestUrl: String): Box[JsonDynamicResourceDoc] =
    DynamicResourceDoc.findByVerbAndUrl(bankId, requestVerb, requestUrl)
      .map(DynamicResourceDoc.getJsonDynamicResourceDoc)

  override def getAllAndConvert[T: Manifest](bankId: Option[String], transform: JsonDynamicResourceDoc => T): List[T] = {
    val cacheKey = (bankId.toString+transform.toString()).intern()
    // Scala 3's Manifest support does not compose Manifest[List[T]] from an in-scope Manifest[T]
    // the way Scala 2 did implicitly - Manifest.classType is a plain factory method (not implicit
    // derivation), so it still works to build it explicitly.
    implicit val listManifest: Manifest[List[T]] = Manifest.classType(classOf[List[_]].asInstanceOf[Class[List[T]]], manifest[T])
    Caching.memoizeSyncWithImMemory(Some(cacheKey))(getDynamicResourceDocTTL.seconds){
      DynamicResourceDoc.findAll(bankId)
        .map(doc => transform(DynamicResourceDoc.getJsonDynamicResourceDoc(doc)))
    }
  }

  override def create(bankId: Option[String], entity: JsonDynamicResourceDoc,
                      createdByUserId: Option[String]): Box[JsonDynamicResourceDoc] =
    tryo {
      DynamicResourceDoc.insert(
        dynamicResourceDocId = APIUtil.generateUUID(),
        bankId = bankId,
        partialFunctionName = entity.partialFunctionName,
        requestVerb = entity.requestVerb,
        requestUrl = entity.requestUrl,
        summary = entity.summary,
        description = entity.description,
        exampleRequestBody = entity.exampleRequestBody.map(json.compactRender(_)),
        successResponseBody = entity.successResponseBody.map(json.compactRender(_)),
        errorResponseBodies = entity.errorResponseBodies,
        tags = entity.tags,
        roles = entity.roles,
        methodBody = entity.methodBody,
        // Provenance comes from the authenticated user and a hash computed here - never from the
        // request body, which the caller controls.
        createdByUserId = createdByUserId,
        methodBodyHash = Some(APIUtil.sha256Hex(entity.decodedMethodBody)))
    }.map(DynamicResourceDoc.getJsonDynamicResourceDoc)

  override def update(bankId: Option[String], entity: JsonDynamicResourceDoc,
                      updatedByUserId: Option[String]): Box[JsonDynamicResourceDoc] = {
    // The lookup deliberately ignores bankId — Mapper's did too — so an update addressed by id
    // finds the doc whatever its scope, and then writes the supplied bankId onto it.
    val currentId = entity.dynamicResourceDocId.getOrElse("")
    DynamicResourceDoc.findById(None, currentId) match {
      case Full(_) =>
        tryo {
          DynamicResourceDoc.update(
            dynamicResourceDocId = currentId,
            bankId = bankId,
            partialFunctionName = entity.partialFunctionName,
            requestVerb = entity.requestVerb,
            requestUrl = entity.requestUrl,
            summary = entity.summary,
            description = entity.description,
            exampleRequestBody = entity.exampleRequestBody.map(json.compactRender(_)),
            successResponseBody = entity.successResponseBody.map(json.compactRender(_)),
            errorResponseBodies = entity.errorResponseBodies,
            tags = entity.tags,
            roles = entity.roles,
            methodBody = entity.methodBody,
            updatedByUserId = updatedByUserId,
            methodBodyHash = Some(APIUtil.sha256Hex(entity.decodedMethodBody)))
        }.flatMap(box => box).map(DynamicResourceDoc.getJsonDynamicResourceDoc)
      case _ => Empty
    }
  }

  override def deleteById(bankId: Option[String], id: String): Box[Boolean] =
    tryo(DynamicResourceDoc.delete(bankId, id))
}
