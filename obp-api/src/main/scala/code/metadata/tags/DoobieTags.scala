package code.metadata.tags

import java.sql.Timestamp
import java.util.{Date, UUID}

import code.api.util.DoobieUtil
import code.users.Users
import code.views.{Views => MetaViews}
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._  // Meta instances for java.sql.Timestamp
import net.liftweb.common.{Box, Empty}
import net.liftweb.util.Helpers.tryo

object DoobieTags extends Tags {

  private case class TagRow(
    id: Long,
    userId: Option[Long],
    tag: Option[String],
    view: Option[String],
    date: Option[Timestamp],
    account: Option[String],
    tagId: Option[String],
    bank: Option[String],
    transaction: Option[String]
  )

  private case class DoobieTag(row: TagRow) extends TransactionTag {
    override def id_ : String          = row.tagId.getOrElse("")
    override def datePosted: Date      = row.date.map(t => new Date(t.getTime)).getOrElse(new Date(0))
    override def postedBy: Box[User]   = row.userId.fold[Box[User]](Empty)(Users.users.vend.getUserByResourceUserId)
    override def viewId: ViewId        = ViewId(row.view.getOrElse(""))
    override def value: String         = row.tag.getOrElse("")
  }

  private val selectCols: Fragment =
    fr"SELECT id, user_c, tag, view_c, date_c, account, tagid, bank, transaction_c FROM mappedtag"

  override def getTags(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionTag] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val q = (selectCols ++ fr"""WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value} AND view_c = $metaViewId""")
      .query[TagRow].to[List]
    DoobieUtil.runQuery(q).map(DoobieTag(_))
  }

  override def getTagsOnAccount(bankId: BankId, accountId: AccountId)(viewId: ViewId): List[TransactionTag] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val q = (selectCols ++ fr"""WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c IS NULL AND view_c = $metaViewId""")
      .query[TagRow].to[List]
    DoobieUtil.runQuery(q).map(DoobieTag(_))
  }

  override def addTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                     (userId: UserPrimaryKey, viewId: ViewId, tagText: String, datePosted: Date): Box[TransactionTag] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      val tagId = UUID.randomUUID().toString
      val ts    = new Timestamp(datePosted.getTime)
      val txId  = transactionId.value
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedtag (user_c, tag, view_c, date_c, account, tagid, bank, transaction_c)
              VALUES (${userId.value}, $tagText, $metaViewId, $ts, ${accountId.value}, $tagId, ${bankId.value}, $txId)"""
          .update.run
      )
      DoobieTag(TagRow(0L, Some(userId.value), Some(tagText), Some(metaViewId), Some(ts),
        Some(accountId.value), Some(tagId), Some(bankId.value), Some(txId)))
    }
  }

  override def addTagOnAccount(bankId: BankId, accountId: AccountId)
                               (userId: UserPrimaryKey, viewId: ViewId, tagText: String, datePosted: Date): Box[TransactionTag] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      val tagId = UUID.randomUUID().toString
      val ts    = new Timestamp(datePosted.getTime)
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedtag (user_c, tag, view_c, date_c, account, tagid, bank)
              VALUES (${userId.value}, $tagText, $metaViewId, $ts, ${accountId.value}, $tagId, ${bankId.value})"""
          .update.run
      )
      DoobieTag(TagRow(0L, Some(userId.value), Some(tagText), Some(metaViewId), Some(ts),
        Some(accountId.value), Some(tagId), Some(bankId.value), None))
    }
  }

  override def deleteTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(tagId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedtag WHERE tagid = $tagId".update.run
      ) > 0
    }

  override def deleteTagOnAccount(bankId: BankId, accountId: AccountId)(tagId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedtag WHERE tagid = $tagId
              AND bank = ${bankId.value} AND account = ${accountId.value}""".update.run
      ) > 0
    }

  override def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedtag WHERE bank = ${bankId.value} AND account = ${accountId.value}".update.run
      )
    }.isDefined

  override def bulkDeleteTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedtag WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value}""".update.run
      )
    }.isDefined

  def countByBankAccountTransaction(bankId: String, accountId: String, transactionId: String): Int =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mappedtag
            WHERE bank = $bankId AND account = $accountId AND transaction_c = $transactionId"""
        .query[Int].unique
    )
}
