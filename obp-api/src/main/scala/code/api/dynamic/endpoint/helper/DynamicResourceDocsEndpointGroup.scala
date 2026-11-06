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

package code.api.dynamic.endpoint.helper

import code.api.util.APIUtil.ResourceDoc
import code.api.util.{APIUtil, ApiRole, ApiTag}
import code.dynamicResourceDoc.{DynamicResourceDocProvider, JsonDynamicResourceDoc}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

object DynamicResourceDocsEndpointGroup extends EndpointGroup with code.util.Helper.MdcLoggable {
  override lazy val urlPrefix: String = APIUtil.getPropsValue("url.prefix.dynamic.resourceDoc", "dynamic-resource-doc")


  override protected def resourceDocs: List[APIUtil.ResourceDoc] =
    // Per-row isolation: a stored methodBody written against the deprecated Lift contract
    // (request.json / Box[JsonResponse] / Full(errorJsonResponse(...))) will fail to compile under
    // the native http4s template. Skip (and log) such a row so one bad endpoint does not crash the
    // whole group / server boot. Re-author the body against the new native contract (see PractiseEndpoint).
    // Maker/checker execution guard: only active rows whose body hash a checker approved are served
    // (the hash check applies only when dynamic_code_requires_approval covers DYNAMIC_RESOURCE_DOC).
    DynamicResourceDocProvider.provider.vend.getAll(None)
      .filter(_.dynamicResourceDocId.exists(code.dynamicchangerequest.MakerChecker.isExecutableDynamicResourceDoc))
      .flatMap { dynamicDoc =>
      try {
        Some(toResourceDoc(dynamicDoc))
      } catch {
        case e: Throwable =>
          logger.error(s"[DynamicResourceDocsEndpointGroup] skipping dynamic resource doc '${dynamicDoc.requestVerb} ${dynamicDoc.requestUrl}' " +
            s"(id=${dynamicDoc.dynamicResourceDocId.getOrElse("")}): its methodBody could not be compiled under the native http4s contract. " +
            s"It is likely stored under the deprecated Lift contract — re-author the body against the new native contract. Cause: ${e.getMessage}", e)
          None
      }
    }

  private val apiVersion : ScannedApiVersion = ApiVersion.v4_0_0

  /**
   * this is a function, convert JsonDynamicResourceDoc => ResourceDoc
   * 
   * the core difference between JsonDynamicResourceDoc and ResourceDoc are the following:
   * 
   * 1st: JsonDynamicResourceDoc.methodBody <---vs---> ResourceDoc no methodBody
   * 
   * 2rd: JsonDynamicResourceDoc.exampleRequestBody : Option[JValue] <---vs---> ResourceDoc.exampleRequestBody: scala.Product
   * 
   * 3rd: JsonDynamicResourceDoc no partialFunction <---vs---> partialFunction: OBPEndpoint
   * 
   * ....
   * 
   * We need to prepare the ResourceDoc fields from JsonDynamicResourceDoc.
   * @CompiledObjects also see this class,
   * 
   */
  private val toResourceDoc: JsonDynamicResourceDoc => ResourceDoc = { dynamicDoc =>
    val compiledObjects = CompiledObjects(dynamicDoc.exampleRequestBody, dynamicDoc.successResponseBody, dynamicDoc.methodBody)
    ResourceDoc(
      // partialFunction is a no-op stub — the runtime dispatch uses the native handler in
      // dynamicHttp4sFunction (the compiled artifact is OBPEndpointIO, not the Lift OBPEndpoint).
      dynamicHttp4sFunction = Some(compiledObjects.sandboxEndpoint(dynamicDoc.bankId)),
      implementedInApiVersion = apiVersion,
      partialFunctionName = dynamicDoc.partialFunctionName + "_" + (dynamicDoc.requestVerb + dynamicDoc.requestUrl).hashCode,
      requestVerb = dynamicDoc.requestVerb,
      requestUrl = dynamicDoc.requestUrl,
      summary = dynamicDoc.summary,
      description = dynamicDoc.description,
      exampleRequestBody = compiledObjects.requestBody,// compiled case object
      successResponseBody = compiledObjects.successResponse, //compiled case object
      errorResponseBodies = StringUtils.split(dynamicDoc.errorResponseBodies,",").toList,
      tags = dynamicDoc.tags.split(",").map(ApiTag(_)).toList,
      roles = Option(dynamicDoc.roles)
        .filter(StringUtils.isNoneBlank(_))
        .map { it =>
            StringUtils.split(it, ",")
              .map(ApiRole.getOrCreateDynamicApiRole(_))
              .toList
        }
    )
  }
}
