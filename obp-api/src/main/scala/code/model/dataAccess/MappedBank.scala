package code.model.dataAccess

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{Bank, BankId}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * A bank.
 *
 * `permalink` is the bank id used in URLs and referenced by every other table, but nothing makes it
 * unique - the entity carried a plain index with a note that a unique one would be right, held back
 * by tests that create the same bank twice. Reads therefore take the first match by insertion order
 * rather than assuming there is only one.
 *
 * `createdByUserId` is the user behind POST /my/banks. It is empty for banks created before the
 * column existed and for paths with no authenticated user, and is never serialized into any API
 * response - it exists for the self-service quota and the GET /my/banks listing.
 *
 * The field names are the ones the `Bank` trait declares, not the column names, because the proxy
 * connector serializes a result to JSON and re-extracts it as BankCommons: a row whose bank id sat
 * under a different name would come back with a null bankId.
 */
case class MappedBank(
  bankId: BankId,
  fullName: String,
  shortName: String,
  logoUrl: String,
  websiteUrl: String,
  swiftBic: String,
  nationalIdentifier: String,
  bankRoutingScheme: String,
  bankRoutingAddress: String,
  createdByUserId: String
) extends Bank

object MappedBank {

  private val selectColumns =
    fr"""SELECT permalink, fullbankname, shortbankname, logourl, websiteurl, swiftbic,
                national_identifier, mbankroutingscheme, mbankroutingaddress, createdbyuserid
         FROM mappedbank"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedBank = row match {
    case (permalink, fullBankName, shortBankName, logoURL, websiteURL, swiftBIC,
          nationalIdentifier, bankRoutingScheme, bankRoutingAddress, createdByUserId) =>
      MappedBank(BankId(permalink.orNull), fullBankName.orNull, shortBankName.orNull, logoURL.orNull,
        websiteURL.orNull, swiftBIC.orNull, nationalIdentifier.orNull, bankRoutingScheme.orNull,
        bankRoutingAddress.orNull, createdByUserId.orNull)
  }

  private def query(condition: Fragment): List[MappedBank] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def one(condition: Fragment): Box[MappedBank] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByBankId(bankId: BankId): Box[MappedBank] =
    one(fr"WHERE permalink = ${opt(bankId.value)}")

  def findByNationalIdentifier(nationalIdentifier: String): Box[MappedBank] =
    one(fr"WHERE national_identifier = ${opt(nationalIdentifier)}")

  def findAll(): List[MappedBank] = query(Fragment.empty)

  def findAllByCreatedByUserIds(createdByUserIds: List[String]): List[MappedBank] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows - not "no filter".
    if (createdByUserIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"createdbyuserid",
        cats.data.NonEmptyList.fromListUnsafe(createdByUserIds.distinct))
      query(fr"WHERE " ++ in)
    }

  def countByCreatedByUserIds(createdByUserIds: List[String]): Long =
    if (createdByUserIds.isEmpty) 0L
    else {
      val in = Fragments.in(fr"createdbyuserid",
        cats.data.NonEmptyList.fromListUnsafe(createdByUserIds.distinct))
      DoobieUtil.runQuery(
        (fr"SELECT COUNT(*) FROM mappedbank WHERE " ++ in).query[Long].unique)
    }

  def insert(bankId: String, fullBankName: String, shortBankName: String, logoURL: String,
             websiteURL: String, swiftBIC: String, nationalIdentifier: String,
             bankRoutingScheme: String, bankRoutingAddress: String,
             createdByUserId: String): MappedBank = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedbank
            (permalink, fullbankname, shortbankname, logourl, websiteurl, swiftbic,
             national_identifier, mbankroutingscheme, mbankroutingaddress, createdbyuserid,
             createdat, updatedat)
            VALUES (${opt(bankId)}, ${opt(fullBankName)}, ${opt(shortBankName)}, ${opt(logoURL)},
             ${opt(websiteURL)}, ${opt(swiftBIC)}, ${opt(nationalIdentifier)},
             ${opt(bankRoutingScheme)}, ${opt(bankRoutingAddress)}, ${opt(createdByUserId)},
             $now, $now)"""
        .update.run)
    MappedBank(BankId(bankId), fullBankName, shortBankName, logoURL, websiteURL, swiftBIC,
      nationalIdentifier, bankRoutingScheme, bankRoutingAddress, createdByUserId)
  }

  /**
   * Rewrites everything except createdByUserId, which belongs to whoever created the bank and is
   * not touched by an update.
   */
  def updateByBankId(bankId: String, fullBankName: String, shortBankName: String, logoURL: String,
                     websiteURL: String, swiftBIC: String, nationalIdentifier: String,
                     bankRoutingScheme: String, bankRoutingAddress: String): Box[MappedBank] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedbank
            SET fullbankname = ${opt(fullBankName)}, shortbankname = ${opt(shortBankName)},
                logourl = ${opt(logoURL)}, websiteurl = ${opt(websiteURL)},
                swiftbic = ${opt(swiftBIC)}, national_identifier = ${opt(nationalIdentifier)},
                mbankroutingscheme = ${opt(bankRoutingScheme)},
                mbankroutingaddress = ${opt(bankRoutingAddress)}, updatedat = $now
            WHERE permalink = ${opt(bankId)}"""
        .update.run)
    findByBankId(BankId(bankId))
  }

  def deleteByBankId(bankId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbank WHERE permalink = ${opt(bankId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbank".update.run)
    ()
  }
}
