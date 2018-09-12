package code.metadata.tags

import java.util.Date

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util._
import code.views.Views
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo
import net.liftweb.mapper._

object MappedTags extends Tags {
  override def getTags(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionTag] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    MappedTag.findAll(MappedTag.findQuery(bankId, accountId, transactionId, ViewId(metadateViewId)): _*)
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

  override def deleteTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(tagId: String): Box[Boolean] = {
    //tagId is always unique so we actually don't need to use bankId, accountId, or transactionId
    MappedTag.find(By(MappedTag.tagId, tagId)).map(_.delete_!)
  }

  override def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean = {
    val tagsDeleted = MappedTag.bulkDelete_!!(
      By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountId.value)
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