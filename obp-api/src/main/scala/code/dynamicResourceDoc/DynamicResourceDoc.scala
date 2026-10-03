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
import code.util.UUIDString
import com.openbankproject.commons.util.json
import net.liftweb.mapper._
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

class DynamicResourceDoc extends LongKeyedMapper[DynamicResourceDoc] with IdPK with CreatedUpdated {

  override def getSingleton = DynamicResourceDoc

  object BankId extends MappedString(this, 255)
  object DynamicResourceDocId extends UUIDString(this)
  object PartialFunctionName extends MappedString(this, 255)
  object RequestVerb extends MappedString(this, 255)
  object RequestUrl extends MappedString(this, 255)
  object Summary extends MappedString(this, 255)
  object Description extends MappedText(this)
  object ExampleRequestBody extends MappedText(this)
  object SuccessResponseBody extends MappedText(this)
  object ErrorResponseBodies extends MappedText(this)
  object Tags extends MappedText(this)
  object Roles extends MappedText(this)
  object MethodBody extends MappedText(this)
  // Source language of MethodBody: "Scala" (default) or "Java". Mirrors DynamicMessageDoc.Lang /
  // ConnectorMethod.programmingLang — same field name/width convention, see DynamicEndpoints.
  object Lang extends MappedString(this, 50)
  // Provenance: who created / last updated this runtime-compiled endpoint, and a SHA-256 of the
  // (decoded) method body so tampering / drift is detectable. Set server-side from the CallContext
  // user — never from the request body. createdAt / updatedAt come from the CreatedUpdated trait.
  object CreatedByUserId extends MappedString(this, 255)
  object UpdatedByUserId extends MappedString(this, 255)
  object MethodBodyHash extends MappedString(this, 64)
  // Maker/checker (see docs/MAKER_CHECKER_DYNAMIC_CODE_DESIGN.md): the runtime only loads this row when
  // IsActive is true and, when maker/checker is enabled for this target type, when MethodBodyHash
  // equals ApprovedHash. ApprovedHash is written only by an approved DynamicChangeRequest (or the
  // one-off seeding of pre-existing rows when the feature is first enabled), never from a request body.
  object ApprovedHash extends MappedString(this, 64)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }

}


object DynamicResourceDoc extends DynamicResourceDoc with LongKeyedMetaMapper[DynamicResourceDoc] {
  // A verb and URL are unique within a space (BankId, which is SYS for the system space), not across spaces:
  // a doc is served under its space. Databases that predate this had UniqueIndex(RequestUrl, RequestVerb) and
  // stored NULL for a system level doc; Migration.database.prepareDynamicEntitySpaceScopedIndexes, which Boot
  // runs on every start, drops that index and moves those NULLs to SYS.
  override def dbIndexes: List[BaseIndex[DynamicResourceDoc]] = UniqueIndex(DynamicResourceDocId) :: UniqueIndex(BankId, RequestUrl, RequestVerb) :: super.dbIndexes
  def getJsonDynamicResourceDoc(dynamicResourceDoc: DynamicResourceDoc) = JsonDynamicResourceDoc(
    // The row stores SYS for the system space; in memory the system space is None, as for a Dynamic Entity.
    bankId = code.api.dynamic.entity.helper.DynamicEntitySpace.bankIdOrNoneForSystem(dynamicResourceDoc.BankId.get),
    dynamicResourceDocId = Some(dynamicResourceDoc.DynamicResourceDocId.get),
    methodBody = dynamicResourceDoc.MethodBody.get,
    partialFunctionName = dynamicResourceDoc.PartialFunctionName.get,
    requestVerb = dynamicResourceDoc.RequestVerb.get,
    requestUrl = dynamicResourceDoc.RequestUrl.get,
    summary = dynamicResourceDoc.Summary.get,
    description = dynamicResourceDoc.Description.get,
    exampleRequestBody = Option(dynamicResourceDoc.ExampleRequestBody.get).filter(StringUtils.isNotBlank).map(json.parse),
    successResponseBody = Option(dynamicResourceDoc.SuccessResponseBody.get).filter(StringUtils.isNotBlank).map(json.parse),
    errorResponseBodies = dynamicResourceDoc.ErrorResponseBodies.get,
    tags = dynamicResourceDoc.Tags.get,
    roles = dynamicResourceDoc.Roles.get,
    // Rows created before the Lang column existed have NULL there, not "Scala" -- a bare
    // Lang.get would surface that as an empty/null programming_lang instead of falling back to
    // JsonDynamicResourceDoc's own "Scala" default, since an explicit null argument bypasses a
    // case class default (that only applies when the argument is omitted entirely).
    programmingLang = Option(dynamicResourceDoc.Lang.get).filter(StringUtils.isNotBlank).getOrElse("Scala")
  )
}

