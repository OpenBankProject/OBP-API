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

package code.apicollectionendpoint

import code.util.MappedUUID
import net.liftweb.mapper._

class ApiCollectionEndpoint extends ApiCollectionEndpointTrait with LongKeyedMapper[ApiCollectionEndpoint] with IdPK with CreatedUpdated {
  def getSingleton = ApiCollectionEndpoint

  object ApiCollectionEndpointId extends MappedUUID(this)
  object ApiCollectionId extends MappedString(this, 100)
  object OperationId extends MappedString(this, 100)

  override def apiCollectionEndpointId: String = ApiCollectionEndpointId.get    
  override def apiCollectionId: String = ApiCollectionId.get    
  override def operationId: String = OperationId.get    
}

object ApiCollectionEndpoint extends ApiCollectionEndpoint with LongKeyedMetaMapper[ApiCollectionEndpoint] {
  override def dbIndexes = UniqueIndex(ApiCollectionEndpointId) :: UniqueIndex(ApiCollectionId, OperationId) ::  super.dbIndexes
}

trait ApiCollectionEndpointTrait {
  def apiCollectionEndpointId: String
  def apiCollectionId: String
  def operationId: String
}
