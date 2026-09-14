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

package code.metadata.tags

import java.util.Date

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util._
import code.views.Views
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo
import net.liftweb.mapper._

object MappedTags extends Tags {
  override def getTags(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionTag] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    MappedTag.findAll(MappedTag.findQuery(bankId, accountId, transactionId, ViewId(metadateViewId)): _*)
  }
  override def getTagsOnAccount(bankId: BankId, accountId: AccountId)(viewId: ViewId): List[TransactionTag] = {
    val metadataViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    MappedTag.findAll( By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountId.value) ,
      NullRef(MappedTag.transaction),
      By(MappedTag.view, ViewId(metadataViewId).value))
  }

  override def addTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                     (userId: UserPrimaryKey, viewId: ViewId, tagText: String, datePosted: Date): Box[TransactionTag] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo{
      MappedTag.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(metadateViewId)
        .user(userId.value)
        .tag(tagText)
        .date(datePosted).saveMe
    }
  }
  
  override def addTagOnAccount(bankId: BankId, accountId: AccountId)
                     (userId: UserPrimaryKey, viewId: ViewId, tagText: String, datePosted: Date): Box[TransactionTag] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo{
      MappedTag.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(null)
        .view(metadateViewId)
        .user(userId.value)
        .tag(tagText)
        .date(datePosted).saveMe
    }
  }

  override def deleteTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(tagId: String): Box[Boolean] = {
    //tagId is always unique so we actually don't need to use bankId, accountId, or transactionId
    MappedTag.find(By(MappedTag.tagId, tagId)).map(_.delete_!)
  }
  override def deleteTagOnAccount(bankId: BankId, accountId: AccountId)(tagId: String): Box[Boolean] = {
    //tagId is always unique so we actually don't need to use bankId, accountId, or transactionId
    MappedTag.find(By(MappedTag.tagId, tagId), By(MappedTag.bank, bankId.value), By(MappedTag.account, accountId.value)).map(_.delete_!)
  }

  override def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean = {
    val tagsDeleted = MappedTag.bulkDelete_!!(
      By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountId.value)
    )
    tagsDeleted
  }
  override def bulkDeleteTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = {
    val tagsDeleted = MappedTag.bulkDelete_!!(
      By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountId.value),
      By(MappedTag.transaction, transactionId.value)
    )
    tagsDeleted
  }
}

class MappedTag extends TransactionTag with LongKeyedMapper[MappedTag] with IdPK with CreatedUpdated {
  def getSingleton = MappedTag

  object bank extends UUIDString(this)
  object account extends AccountIdString(this)
  object transaction extends UUIDString(this)
  object view extends MediumString(this)

  object tagId extends MappedUUID(this)

  object user extends MappedLongForeignKey(this, ResourceUser)
  object tag extends MappedString(this, 64)
  object date extends MappedDateTime(this)

  override def id_ : String = tagId.get
  override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(user.get)
  override def value: String = tag.get
  override def viewId: ViewId = ViewId(view.get)
  override def datePosted: Date = date.get
}

object MappedTag extends MappedTag with LongKeyedMetaMapper[MappedTag] {
  override def dbIndexes = Index(bank, account, transaction, view) :: UniqueIndex(tagId) :: super.dbIndexes

  def findQuery(bankId: BankId, accountId: AccountId, transactionId: TransactionId, viewId: ViewId) =
    By(MappedTag.bank, bankId.value) ::
    By(MappedTag.account, accountId.value) ::
    By(MappedTag.transaction, transactionId.value) ::
    By(MappedTag.view, viewId.value) :: Nil
}