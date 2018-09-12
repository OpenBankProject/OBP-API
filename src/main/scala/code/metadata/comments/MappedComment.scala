package code.metadata.comments

import java.util.{Date, UUID}

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util.{AccountIdString, MappedUUID, UUIDString}
import code.views.Views
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedComments extends Comments {
  override def getComments(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[Comment] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    MappedComment.findAll(
      By(MappedComment.bank, bankId.value),
      By(MappedComment.account, accountId.value),
      By(MappedComment.transaction, transactionId.value),
      By(MappedComment.view, metadateViewId))
  }

  override def deleteComment(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(commentId: String): Box[Boolean] = {
    val deleted = for {
      comment <- MappedComment.find(By(MappedComment.bank, bankId.value),
        By(MappedComment.account, accountId.value),
        By(MappedComment.transaction, transactionId.value),
        By(MappedComment.apiId, commentId))
    } yield comment.delete_!

    deleted match {
      case Full(true) => Full(true)
      case _ => Failure("Could not delete comment")
    }
  }

  override def addComment(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId: ViewId, text: String, datePosted: Date): Box[Comment] = {
    val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      MappedComment.create
        .bank(bankId.value)
        .account(accountId.value)
        .transaction(transactionId.value)
        .poster(userId.value)
        .view(metadateViewId)
        .text_(text)
        .date(datePosted).saveMe
    }
  }

  override def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean = {
    val commentsDeleted = MappedComment.bulkDelete_!!(
      By(MappedComment.bank, bankId.value),
      By(MappedComment.account, accountId.value)
    )
    commentsDeleted
  }

}

class MappedComment extends Comment with LongKeyedMapper[MappedComment] with IdPK with CreatedUpdated {

  def getSingleton = MappedComment

  object apiId extends MappedUUID(this)

  object text_ extends MappedString(this, 2000)
  object poster extends MappedLongForeignKey(this, ResourceUser)
  object replyTo extends MappedUUID(this) {
    override def defaultValue = ""
  }

  object view extends UUIDString(this)
  object date extends MappedDateTime(this)

  object bank extends UUIDString(this)
  object account extends AccountIdString(this)
  object transaction extends UUIDString(this)

  override def id_ : String = apiId.get
  override def text: String = text_.get
  override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(poster.get)
  override def replyToID: String = replyTo.get
  override def viewId: ViewId = ViewId(view.get)
  override def datePosted: Date = date.get
}

object MappedComment extends MappedComment with LongKeyedMetaMapper[MappedComment] {
  override def dbIndexes = UniqueIndex(apiId) :: Index(view, bank, account, transaction) :: super.dbIndexes
}
