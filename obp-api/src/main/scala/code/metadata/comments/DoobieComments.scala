package code.metadata.comments

import java.sql.Timestamp
import java.util.{Date, UUID}

import code.api.util.DoobieUtil
import code.users.Users
import code.views.{Views => MetaViews}
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._  // Meta instances for java.sql.Timestamp
import net.liftweb.common.{Box, Empty, Failure}
import net.liftweb.util.Helpers.tryo

object DoobieComments extends Comments {

  private case class CommentRow(
    id: Long,
    view: Option[String],
    date: Option[Timestamp],
    account: Option[String],
    apiId: Option[String],
    text: Option[String],
    poster: Option[Long],
    replyTo: Option[String],
    bank: Option[String],
    transaction: Option[String]
  )

  private case class DoobieComment(row: CommentRow) extends Comment {
    override def id_ : String          = row.apiId.getOrElse("")
    override def text: String          = row.text.getOrElse("")
    override def postedBy: Box[User]   = row.poster.fold[Box[User]](Empty)(Users.users.vend.getUserByResourceUserId)
    override def replyToID: String     = row.replyTo.getOrElse("")
    override def viewId: ViewId        = ViewId(row.view.getOrElse(""))
    override def datePosted: Date      = row.date.map(t => new Date(t.getTime)).getOrElse(new Date(0))
  }

  private val selectCols: Fragment =
    fr"SELECT id, view_c, date_c, account, apiid, text_, poster, replyto, bank, transaction_c FROM mappedcomment"

  override def getComments(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[Comment] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val q = (selectCols ++ fr"""WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value} AND view_c = $metaViewId""")
      .query[CommentRow].to[List]
    DoobieUtil.runQuery(q).map(DoobieComment(_))
  }

  override def addComment(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                          (userId: UserPrimaryKey, viewId: ViewId, text: String, datePosted: Date): Box[Comment] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      val commentId = UUID.randomUUID().toString
      val ts        = new Timestamp(datePosted.getTime)
      val txId      = transactionId.value
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedcomment (view_c, date_c, account, apiid, text_, poster, bank, transaction_c)
              VALUES ($metaViewId, $ts, ${accountId.value}, $commentId, $text, ${userId.value}, ${bankId.value}, $txId)"""
          .update.run
      )
      DoobieComment(CommentRow(0L, Some(metaViewId), Some(ts), Some(accountId.value),
        Some(commentId), Some(text), Some(userId.value), Some(""), Some(bankId.value), Some(txId)))
    }
  }

  override def deleteComment(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(commentId: String): Box[Boolean] = {
    val deleted = tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedcomment WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value} AND apiid = $commentId""".update.run
      )
    }
    deleted match {
      case net.liftweb.common.Full(n) if n > 0 => net.liftweb.common.Full(true)
      case _ => Failure("Could not delete comment")
    }
  }

  override def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedcomment WHERE bank = ${bankId.value} AND account = ${accountId.value}".update.run
      )
    }.isDefined

  override def bulkDeleteCommentsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedcomment WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value}""".update.run
      )
    }.isDefined

  def countByBankAccountTransaction(bankId: String, accountId: String, transactionId: String): Int =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mappedcomment
            WHERE bank = $bankId AND account = $accountId AND transaction_c = $transactionId"""
        .query[Int].unique
    )
}
