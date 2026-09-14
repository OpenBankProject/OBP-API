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

import code.util.UUIDString
import net.liftweb.mapper._

class ConnectorMethod extends LongKeyedMapper[ConnectorMethod] with IdPK with CreatedUpdated {

  override def getSingleton = ConnectorMethod

  object ConnectorMethodId extends UUIDString(this)
  object MethodName extends MappedString(this, 255)

  object MethodBody extends MappedText(this)

  object Lang extends MappedString(this, 50)
  // Provenance for this runtime-compiled connector method: who created / last updated it and a
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


object ConnectorMethod extends ConnectorMethod with LongKeyedMetaMapper[ConnectorMethod] {
  override def dbIndexes: List[BaseIndex[ConnectorMethod]] = UniqueIndex(ConnectorMethodId) :: UniqueIndex(MethodName) :: super.dbIndexes

  // Note: provenance (CreatedByUserId / UpdatedByUserId / MethodBodyHash / createdAt / updatedAt) is
  // captured in the columns above but intentionally NOT surfaced in this v4.0.0 (STABLE) JSON — the
  // v4 response shape is frozen. It will be exposed via a new (v7) endpoint version.
  def getJsonConnectorMethod(it: ConnectorMethod): JsonConnectorMethod = JsonConnectorMethod(
    connectorMethodId = Some(it.ConnectorMethodId.get),
    methodName = it.MethodName.get,
    methodBody = it.MethodBody.get,
    programmingLang = Option(it.Lang.get).getOrElse("Scala")
  )
}

