package code.metadata.tags

import java.util.Date

import code.model._
import code.model.dataAccess.APIUser
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo
import net.liftweb.mapper._

object MappedTags extends Tags {
  override def getTags(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionTag] = {
    MappedTag.findAll(MappedTag.findQuery(bankId, accountId, transactionId, viewId): _*)
  }

  override def addTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                     (userId: UserId, viewId: ViewId, tagText: String, datePosted: Date): Box[TransactionTag] = {
    tryo{
      MappedTag.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .view(viewId.value)
        .user(userId.value)
        .tag(tagText)
        .date(datePosted).saveMe
    }
  }

  override def deleteTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(tagId: String): Box[Unit] = {
    //tagId is always unique so we actually don't need to use bankId, accountId, or transactionId
    MappedTag.find(By(MappedTag.tagId, tagId)).map(_.delete_!).map(x => ()) //TODO: this should return something more useful than Box[Unit]
  }
}

class MappedTag extends TransactionTag with LongKeyedMapper[MappedTag] with IdPK with CreatedUpdated {
  def getSingleton = MappedTag

  object bank extends MappedString(this, 255)
  object account extends MappedString(this, 255)
  object transaction extends MappedString(this, 255)
  object view extends MappedString(this, 255)

  object tagId extends MappedUUID(this)

  object user extends MappedLongForeignKey(this, APIUser)
  object tag extends MappedString(this, 255)
  object date extends MappedDate(this)

  override def id_ : String = tagId.get
  override def postedBy: Box[User] = user.obj
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