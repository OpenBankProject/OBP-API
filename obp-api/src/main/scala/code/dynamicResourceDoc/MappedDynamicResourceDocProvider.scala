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

import org.json4s._
import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import com.openbankproject.commons.util.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object MappedDynamicResourceDocProvider extends DynamicResourceDocProvider {

  private val getDynamicResourceDocTTL : Int = {
    if(Props.testMode) 0 //make the scala test work
    else APIUtil.getPropsValue(s"dynamicResourceDoc.cache.ttl.seconds", "40").toInt
  }

  override def getById(bankId: Option[String], dynamicResourceDocId: String): Box[JsonDynamicResourceDoc] = { 
    if(bankId.isEmpty){
      DynamicResourceDoc
      .find(By(DynamicResourceDoc.DynamicResourceDocId, dynamicResourceDocId))
      .map(DynamicResourceDoc.getJsonDynamicResourceDoc)
    } else{
      DynamicResourceDoc
        .find(
          By(DynamicResourceDoc.DynamicResourceDocId, dynamicResourceDocId),
          By(DynamicResourceDoc.BankId, bankId.getOrElse("")),
        )
        .map(DynamicResourceDoc.getJsonDynamicResourceDoc)
    }
  }

  override def getByVerbAndUrl(bankId: Option[String], requestVerb: String, requestUrl: String): Box[JsonDynamicResourceDoc] =
    if(bankId.isEmpty){
      DynamicResourceDoc
        .find(By(DynamicResourceDoc.RequestVerb, requestVerb), By(DynamicResourceDoc.RequestUrl, requestUrl))
        .map(DynamicResourceDoc.getJsonDynamicResourceDoc)
    } else{
      DynamicResourceDoc
        .find(
          By(DynamicResourceDoc.BankId, bankId.getOrElse("")), 
          By(DynamicResourceDoc.RequestVerb, requestVerb), 
          By(DynamicResourceDoc.RequestUrl, requestUrl))
        .map(DynamicResourceDoc.getJsonDynamicResourceDoc)
    }
  
  override def getAllAndConvert[T: Manifest](bankId: Option[String], transform: JsonDynamicResourceDoc => T): List[T] = {
    val cacheKey = (bankId.toString+transform.toString()).intern()
    Caching.memoizeSyncWithImMemory(Some(cacheKey))(getDynamicResourceDocTTL.seconds){
        if(bankId.isEmpty){
          DynamicResourceDoc.findAll()
            .map(doc => transform(DynamicResourceDoc.getJsonDynamicResourceDoc(doc)))
        } else {
          DynamicResourceDoc.findAll(
            By(DynamicResourceDoc.BankId, bankId.getOrElse("")))
            .map(doc => transform(DynamicResourceDoc.getJsonDynamicResourceDoc(doc)))
        }
      }
  }

  override def create(bankId: Option[String], entity: JsonDynamicResourceDoc, createdByUserId: Option[String]): Box[JsonDynamicResourceDoc]=
    tryo {
      val requestBody = entity.exampleRequestBody.map(json.compactRender(_)).orNull
      val responseBody = entity.successResponseBody.map(json.compactRender(_)).orNull

      DynamicResourceDoc.create
      .BankId(bankId.getOrElse(null))
      .DynamicResourceDocId(APIUtil.generateUUID())
      .PartialFunctionName(entity.partialFunctionName)
      .RequestVerb(entity.requestVerb)
      .RequestUrl(entity.requestUrl)
      .Summary(entity.summary)
      .Description(entity.description)
      .ExampleRequestBody(requestBody)
      .SuccessResponseBody(responseBody)
      .ErrorResponseBodies(entity.errorResponseBodies)
      .Tags(entity.tags)
      .Roles(entity.roles)
      .MethodBody(entity.methodBody)
      // provenance is set here from the authenticated user + computed hash, not from `entity`
      .CreatedByUserId(createdByUserId.getOrElse(null))
      .MethodBodyHash(APIUtil.sha256Hex(entity.decodedMethodBody))
      .saveMe()
    }.map(DynamicResourceDoc.getJsonDynamicResourceDoc)


  override def update(bankId: Option[String], entity: JsonDynamicResourceDoc, updatedByUserId: Option[String]): Box[JsonDynamicResourceDoc] = {
    DynamicResourceDoc.find(By(DynamicResourceDoc.DynamicResourceDocId, entity.dynamicResourceDocId.getOrElse(""))) match {
      case Full(v) =>
        tryo {
          val requestBody = entity.exampleRequestBody.map(json.compactRender(_)).orNull
          val responseBody = entity.successResponseBody.map(json.compactRender(_)).orNull
          v.PartialFunctionName(entity.partialFunctionName)
            .BankId(bankId.getOrElse(null))
            .RequestVerb(entity.requestVerb)
            .RequestUrl(entity.requestUrl)
            .Summary(entity.summary)
            .Description(entity.description)
            .ExampleRequestBody(requestBody)
            .SuccessResponseBody(responseBody)
            .ErrorResponseBodies(entity.errorResponseBodies)
            .Tags(entity.tags)
            .Roles(entity.roles)
            .MethodBody(entity.methodBody)
            // CreatedByUserId is left untouched; record who last changed the code + refresh the hash
            .UpdatedByUserId(updatedByUserId.getOrElse(null))
            .MethodBodyHash(APIUtil.sha256Hex(entity.decodedMethodBody))
            .saveMe()
        }.map(DynamicResourceDoc.getJsonDynamicResourceDoc)
      case _ => Empty
    }
  }

  override def deleteById(bankId: Option[String], id: String): Box[Boolean] = tryo {
    if(bankId.isEmpty) {
      DynamicResourceDoc.bulkDelete_!!(By(DynamicResourceDoc.DynamicResourceDocId, id))
    }else{
      DynamicResourceDoc.bulkDelete_!!(
        By(DynamicResourceDoc.BankId, bankId.getOrElse("")),
        By(DynamicResourceDoc.DynamicResourceDocId, id)
      )
    }
  }
}


