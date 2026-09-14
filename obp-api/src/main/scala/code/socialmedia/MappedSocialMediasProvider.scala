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

package code.socialmedia

import java.util.Date
import code.model.dataAccess.ResourceUser
import code.util.{UUIDString}
import net.liftweb.mapper._

object MappedSocialMediasProvider extends SocialMediaHandleProvider {

  override def getSocialMedias(customerNumber: String): List[MappedSocialMedia] = {
    MappedSocialMedia.findAll(
      By(MappedSocialMedia.mCustomerNumber, customerNumber),
      OrderBy(MappedSocialMedia.updatedAt, Descending))
  }


  override def addSocialMedias(customerNumber: String, `type`: String, handle: String, dateAdded: Date, dateActivated: Date): Boolean = {
    MappedSocialMedia.create
      .mCustomerNumber(customerNumber)
      .mType(`type`)
      .mHandle(handle)
      .mDateAdded(dateAdded)
      .mDateActivated(dateActivated)
      .save
  }
}

class MappedSocialMedia extends SocialMedia
with LongKeyedMapper[MappedSocialMedia] with IdPK with CreatedUpdated {

  def getSingleton = MappedSocialMedia

  object user extends MappedLongForeignKey(this, ResourceUser)
  object bank extends UUIDString(this)

  object mCustomerNumber extends MappedString(this, 64)
  object mType extends MappedString(this, 16)
  object mHandle extends MappedString(this, 64)
  object mDateAdded extends MappedDateTime(this)
  object mDateActivated extends MappedDateTime(this)


  override def customerNumber: String = mCustomerNumber.get
  override def `type`: String = mType.get
  override def handle: String = mHandle.get
  override def dateAdded: Date = mDateAdded.get
  override def dateActivated: Date = mDateActivated.get
}

object MappedSocialMedia extends MappedSocialMedia with LongKeyedMetaMapper[MappedSocialMedia] {
  override def dbIndexes = UniqueIndex(mCustomerNumber) :: super.dbIndexes
}