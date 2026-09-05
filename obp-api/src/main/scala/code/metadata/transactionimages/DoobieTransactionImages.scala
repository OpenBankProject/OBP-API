package code.metadata.transactionimages

import java.net.URL
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

object DoobieTransactionImages extends TransactionImages {

  private case class ImageRow(
    url: Option[String],
    id: Long,
    userId: Option[Long],
    view: Option[String],
    date: Option[Timestamp],
    account: Option[String],
    imageId: Option[String],
    imageDescription: Option[String],
    bank: Option[String],
    transaction: Option[String]
  )

  private val notFoundUrl = new URL("http://example.com/notfound.png")

  private case class DoobieTransactionImage(row: ImageRow) extends TransactionImage {
    override def id_ : String          = row.imageId.getOrElse("")
    override def datePosted: Date      = row.date.map(t => new Date(t.getTime)).getOrElse(new Date(0))
    override def postedBy: Box[User]   = row.userId.fold[Box[User]](Empty)(Users.users.vend.getUserByResourceUserId)
    override def viewId: ViewId        = ViewId(row.view.getOrElse(""))
    override def description: String   = row.imageDescription.getOrElse("")
    override def imageUrl: URL         = row.url.flatMap(u => tryo(new URL(u)).toOption).getOrElse(notFoundUrl)
  }

  private val selectCols: Fragment =
    fr"SELECT url, id, user_c, view_c, date_c, account, imageid, imagedescription, bank, transaction_c FROM mappedtransactionimage"

  override def getImagesForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): List[TransactionImage] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val q = (selectCols ++ fr"""WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value} AND view_c = $metaViewId""")
      .query[ImageRow].to[List]
    DoobieUtil.runQuery(q).map(DoobieTransactionImage(_))
  }

  override def addTransactionImage(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                                   (userId: UserPrimaryKey, viewId: ViewId, description: String, datePosted: Date, imageURL: String): Box[TransactionImage] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      val imageId = UUID.randomUUID().toString
      val ts      = new Timestamp(datePosted.getTime)
      val txId    = transactionId.value
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedtransactionimage (url, user_c, view_c, date_c, account, imageid, imagedescription, bank, transaction_c)
              VALUES ($imageURL, ${userId.value}, $metaViewId, $ts, ${accountId.value}, $imageId, $description, ${bankId.value}, $txId)"""
          .update.run
      )
      DoobieTransactionImage(ImageRow(Some(imageURL), 0L, Some(userId.value), Some(metaViewId), Some(ts),
        Some(accountId.value), Some(imageId), Some(description), Some(bankId.value), Some(txId)))
    }
  }

  override def deleteTransactionImage(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(imageId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedtransactionimage WHERE imageid = $imageId".update.run
      ) > 0
    }

  override def bulkDeleteImagesOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedtransactionimage WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value}""".update.run
      )
    }.isDefined

  override def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedtransactionimage WHERE bank = ${bankId.value} AND account = ${accountId.value}".update.run
      )
    }.isDefined

  def countByBankAccountTransaction(bankId: String, accountId: String, transactionId: String): Int =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mappedtransactionimage
            WHERE bank = $bankId AND account = $accountId AND transaction_c = $transactionId"""
        .query[Int].unique
    )
}
