package code.metadata.wheretags

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import code.users.Users
import code.views.{Views => MetaViews}
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._  // Meta instances for java.sql.Timestamp
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

object DoobieWhereTags extends WhereTags {

  private case class WhereTagRow(
    id: Long,
    userId: Option[Long],
    view: Option[String],
    date: Option[Timestamp],
    account: Option[String],
    geoLatitude: Option[Double],
    geoLongitude: Option[Double],
    bank: Option[String],
    transaction: Option[String]
  )

  private case class DoobieWhereTag(row: WhereTagRow) extends GeoTag {
    override def datePosted: Date    = row.date.map(t => new Date(t.getTime)).getOrElse(new Date(0))
    override def postedBy: Box[User] = row.userId.fold[Box[User]](Empty)(Users.users.vend.getUserByResourceUserId)
    override def latitude: Double    = row.geoLatitude.getOrElse(0.0)
    override def longitude: Double   = row.geoLongitude.getOrElse(0.0)
  }

  private val selectCols: Fragment =
    fr"SELECT id, user_c, view_c, date_c, account, geolatitude, geolongitude, bank, transaction_c FROM mappedwheretag"

  private def findRow(bankId: String, accountId: String, transactionId: String, viewId: String): Option[WhereTagRow] = {
    val q = (selectCols ++ fr"""WHERE bank = $bankId AND account = $accountId
              AND transaction_c = $transactionId AND view_c = $viewId LIMIT 1""")
      .query[WhereTagRow].option
    DoobieUtil.runQuery(q)
  }

  override def addWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)
                           (userId: UserPrimaryKey, viewId: ViewId, datePosted: Date, longitude: Double, latitude: Double): Boolean = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    val ts         = new Timestamp(datePosted.getTime)
    val existing   = findRow(bankId.value, accountId.value, transactionId.value, metaViewId)
    tryo {
      existing match {
        case Some(row) =>
          DoobieUtil.runUpdate(
            sql"""UPDATE mappedwheretag SET user_c = ${userId.value}, date_c = $ts,
                  geolatitude = $latitude, geolongitude = $longitude
                  WHERE id = ${row.id}""".update.run
          )
        case None =>
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedwheretag (user_c, view_c, date_c, account, geolatitude, geolongitude, bank, transaction_c)
                  VALUES (${userId.value}, $metaViewId, $ts, ${accountId.value}, $latitude, $longitude, ${bankId.value}, ${transactionId.value})"""
              .update.run
          )
      }
    }.isDefined
  }

  override def deleteWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Boolean = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedwheretag WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value} AND view_c = $metaViewId""".update.run
      )
    }.isDefined
  }

  override def getWhereTagForTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Box[GeoTag] = {
    val metaViewId = MetaViews.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
    findRow(bankId.value, accountId.value, transactionId.value, metaViewId)
      .map(r => Full(DoobieWhereTag(r): GeoTag))
      .getOrElse(Empty)
  }

  override def bulkDeleteWhereTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"""DELETE FROM mappedwheretag WHERE bank = ${bankId.value} AND account = ${accountId.value}
              AND transaction_c = ${transactionId.value}""".update.run
      )
    }.isDefined

  override def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedwheretag WHERE bank = ${bankId.value} AND account = ${accountId.value}".update.run
      )
    }.isDefined

  def countByBankAccountTransaction(bankId: String, accountId: String, transactionId: String): Int =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mappedwheretag
            WHERE bank = $bankId AND account = $accountId AND transaction_c = $transactionId"""
        .query[Int].unique
    )
}
