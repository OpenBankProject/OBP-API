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

package code.metadata.transactionimages

import java.net.URL
import java.util.Date

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util.{AccountIdString, MappedUUID, UUIDString}
import code.views.Views
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MapperTransactionImages extends TransactionImages {
  override def getImagesForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionImage] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    MappedTransactionImage.findAll(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountId.value),
      By(MappedTransactionImage.transaction, transactionId.value),
      By(MappedTransactionImage.view, metadateViewId)
    )
  }

  override def deleteTransactionImage(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(imageId: String): Box[Boolean] = {
    //imageId is unique, so we don't need bankId, accountId, and transactionId
    MappedTransactionImage.find(By(MappedTransactionImage.imageId, imageId)).map(_.delete_!)
  }

  override def addTransactionImage(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                                  (userId: UserPrimaryKey, viewId: ViewId, description: String, datePosted: Date, imageURL: String): Box[TransactionImage] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      MappedTransactionImage.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(metadateViewId)
        .user(userId.value)
        .imageDescription(description)
        .url(imageURL.toString)
        .date(datePosted).saveMe
    }
  }

  override def bulkDeleteImagesOnTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId): Boolean = {
    val commentsDeleted = MappedTransactionImage.bulkDelete_!!(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountId.value),
      By(MappedTransactionImage.transaction, transactionId.value)
    )
    commentsDeleted
  }
  
  override def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean = {
    val commentsDeleted = MappedTransactionImage.bulkDelete_!!(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountId.value)
    )
    commentsDeleted
  }
}

class MappedTransactionImage extends TransactionImage with LongKeyedMapper[MappedTransactionImage] with IdPK with CreatedUpdated {
  def getSingleton = MappedTransactionImage

  object bank extends UUIDString(this)
  object account extends AccountIdString(this)
  object transaction extends UUIDString(this)
  object view extends UUIDString(this)

  object imageId extends MappedUUID(this)
  object user extends MappedLongForeignKey(this, ResourceUser)
  object date extends MappedDateTime(this)

  object url extends MappedString(this, 2000) // TODO Introduce / use a class for MappedURL ?
  object imageDescription extends MappedString(this, 2000)

  override def id_ : String = imageId.get
  override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(user.get)
  override def description: String = imageDescription.get
  override def imageUrl: URL = tryo {new URL(url.get)} getOrElse MappedTransactionImage.notFoundUrl
  override def viewId: ViewId = ViewId(view.get)
  override def datePosted: Date = date.get
}

object MappedTransactionImage extends MappedTransactionImage with LongKeyedMetaMapper[MappedTransactionImage] {
  override def dbIndexes = Index(bank, account, transaction, view) :: UniqueIndex(imageId) :: super.dbIndexes


  val notFoundUrl = new URL("http://example.com/notfound.png") //TODO: Make this image exist?
}