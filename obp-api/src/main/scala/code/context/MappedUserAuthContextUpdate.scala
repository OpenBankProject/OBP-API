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

package code.context

import code.api.util.SecureRandomUtil
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.UserAuthContextUpdate
import net.liftweb.mapper._

import scala.util.Random

class MappedUserAuthContextUpdate extends UserAuthContextUpdate with LongKeyedMapper[MappedUserAuthContextUpdate] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserAuthContextUpdate

  object mUserAuthContextUpdateId extends MappedUUID(this)
  object mUserId extends UUIDString(this)
  object mConsumerId extends MappedString(this, 255)
  object mKey extends MappedString(this, 50)
  object mValue extends MappedString(this, 50)
  object mChallenge extends MappedString(this, 10)  {
    override def defaultValue = SecureRandomUtil.csprng.nextInt(99999999).toString()
  }
  object mStatus extends MappedString(this, 20)

  override def userId = mUserId.get   
  override def consumerId: String = mConsumerId.get
  override def key = mKey.get  
  override def value = mValue.get  
  override def userAuthContextUpdateId = mUserAuthContextUpdateId.get
  override def challenge: String = mChallenge.get
  override def status: String = mStatus.get
  
}

object MappedUserAuthContextUpdate extends MappedUserAuthContextUpdate with LongKeyedMetaMapper[MappedUserAuthContextUpdate] {
  override def dbIndexes = super.dbIndexes
}



