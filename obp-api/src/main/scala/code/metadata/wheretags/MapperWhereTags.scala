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

package code.metadata.wheretags

import java.util.Date

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util.{AccountIdString, UUIDString}
import code.views.Views
import com.openbankproject.commons.model._
import net.liftweb.util.Helpers.tryo
import net.liftweb.common.Box
import net.liftweb.mapper._

object MapperWhereTags extends WhereTags {

  private def findMappedWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId, viewId : ViewId) = {
    MappedWhereTag.find(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value),
      By(MappedWhereTag.transaction, transactionId.value),
      By(MappedWhereTag.view, viewId.value))
  }

  override def addWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                          (userId: UserPrimaryKey, viewId: ViewId, datePosted: Date, longitude: Double, latitude: Double): Boolean = {

    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val found = findMappedWhereTag(bankId, accountId, transactionId, ViewId(metadateViewId))

    val toUpdate = found.getOrElse {
      MappedWhereTag.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(metadateViewId)
    }

    toUpdate
      .user(userId.value)
      .date(datePosted)
      .geoLatitude(latitude)
      .geoLongitude(longitude)


    tryo{toUpdate.saveMe}.isDefined
  }

  override def deleteWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Boolean = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val found = findMappedWhereTag(bankId, accountId, transactionId, ViewId(metadateViewId))

    found.map(_.delete_!).getOrElse(false)
  }

  override def getWhereTagForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Box[GeoTag] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    findMappedWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId, ViewId(metadateViewId))
  }

  override def bulkDeleteWhereTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = {
    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value),
      By(MappedWhereTag.transaction, transactionId.value)
    )
    whereTagsDeleted
  }
  
  override def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean = {
    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value)
    )
    whereTagsDeleted
  }
}

class MappedWhereTag extends GeoTag with LongKeyedMapper[MappedWhereTag] with IdPK with CreatedUpdated {

  def getSingleton = MappedWhereTag

  object bank extends UUIDString(this)
  object account extends AccountIdString(this)
  object transaction extends UUIDString(this)
  object view extends UUIDString(this)

  object user extends MappedLongForeignKey(this, ResourceUser)
  object date extends MappedDateTime(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

  override def datePosted: Date = date.get
  override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(user.get)
  override def latitude: Double = geoLatitude.get
  override def longitude: Double = geoLongitude.get
}

object MappedWhereTag extends MappedWhereTag with LongKeyedMetaMapper[MappedWhereTag] {
  override def dbIndexes = Index(bank, account, transaction, view) :: super.dbIndexes
}
