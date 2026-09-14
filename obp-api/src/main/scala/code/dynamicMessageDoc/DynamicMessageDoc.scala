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

package code.dynamicMessageDoc

import org.json4s._
import code.util.UUIDString
import com.openbankproject.commons.util.json
import net.liftweb.mapper._

import scala.collection.immutable.List

class DynamicMessageDoc extends LongKeyedMapper[DynamicMessageDoc] with IdPK with CreatedUpdated {

  override def getSingleton = DynamicMessageDoc

  object BankId extends MappedString(this, 255)
  object DynamicMessageDocId extends UUIDString(this)
  object Process extends MappedString(this, 255)
  object MessageFormat extends MappedString(this, 255)
  object Description extends MappedString(this, 255)
  object OutboundTopic extends MappedString(this, 255)
  object InboundTopic extends MappedString(this, 255)
  object ExampleOutboundMessage extends MappedText(this)
  object ExampleInboundMessage extends MappedText(this)
  object OutboundAvroSchema extends MappedText(this)
  object InboundAvroSchema extends MappedText(this)
  object AdapterImplementation  extends MappedString(this, 255)
  object MethodBody  extends MappedText(this)
  object Lang  extends MappedString(this, 50)
  // Provenance for this runtime-compiled connector function: who created / last updated it and a
  // SHA-256 of the (decoded) method body. Set server-side from the CallContext user, never the
  // request body. createdAt / updatedAt come from the CreatedUpdated trait.
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


object DynamicMessageDoc extends DynamicMessageDoc with LongKeyedMetaMapper[DynamicMessageDoc] {
  override def dbIndexes: List[BaseIndex[DynamicMessageDoc]] = UniqueIndex(DynamicMessageDocId) :: UniqueIndex(Process) :: super.dbIndexes
  def getJsonDynamicMessageDoc(dynamicMessageDoc: DynamicMessageDoc) = JsonDynamicMessageDoc(
    bankId = Some(dynamicMessageDoc.BankId.get),
    dynamicMessageDocId = Some(dynamicMessageDoc.DynamicMessageDocId.get),
    process = dynamicMessageDoc.Process.get,
    messageFormat = dynamicMessageDoc.MessageFormat.get,
    description = dynamicMessageDoc.Description.get,
    outboundTopic = dynamicMessageDoc.OutboundTopic.get,
    inboundTopic = dynamicMessageDoc.InboundTopic.get,
    exampleOutboundMessage = json.parse(dynamicMessageDoc.ExampleOutboundMessage.get),
    exampleInboundMessage = json.parse(dynamicMessageDoc.ExampleInboundMessage.get),
    outboundAvroSchema = dynamicMessageDoc.OutboundAvroSchema.get,
    inboundAvroSchema = dynamicMessageDoc.InboundAvroSchema.get,
    adapterImplementation = dynamicMessageDoc.AdapterImplementation.get,
    methodBody = dynamicMessageDoc.MethodBody.get,
    programmingLang = dynamicMessageDoc.Lang.get
  )
}