package code.fx

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{BankId, FXRate}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/** One FX-rate row, standing in for the Lift entity in return types. */
case class FXRateRow(
  bankId: BankId,
  fromCurrencyCode: String,
  toCurrencyCode: String,
  conversionValue: Double,
  inverseConversionValue: Double,
  effectiveDate: Date
) extends FXRate

/**
 * Doobie implementation of the FX-rate store, replacing the Lift MappedFXRate entity.
 *
 * There is no unique index on this table (see the migration script), so createOrUpdateFXRate's
 * find-then-write is the only thing standing between a repeated call for the same
 * (bankId, from, to) and a duplicate row - matching the Mapper version exactly, gap included.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieFXRateQueries {

  private def rowOf(r: (String, String, String, Double, Double, Timestamp)): FXRateRow =
    FXRateRow(BankId(r._1), r._2, r._3, r._4, r._5, new Date(r._6.getTime))

  private val selectCols: Fragment =
    fr"""SELECT mbankid, mfromcurrencycode, mtocurrencycode, mconversionvalue, minverseconversionvalue, meffectivedate
         FROM mappedfxrate"""

  private def findExact(bankId: String, from: String, to: String): Option[FXRateRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = $bankId AND mfromcurrencycode = $from AND mtocurrencycode = $to LIMIT 1")
        .query[(String, String, String, Double, Double, Timestamp)].option
    ).map(rowOf)

  def findAllForBank(bankId: String): List[FXRateRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = $bankId").query[(String, String, String, Double, Double, Timestamp)].to[List]
    ).map(rowOf)

  /**
   * The latest rate for (fromCurrencyCode, toCurrencyCode); if none, the reverse-order row.
   */
  def find(bankId: String, fromCurrencyCode: String, toCurrencyCode: String): Option[FXRateRow] =
    findExact(bankId, fromCurrencyCode, toCurrencyCode).orElse(findExact(bankId, toCurrencyCode, fromCurrencyCode))

  def createOrUpdate(
    bankId: String,
    fromCurrencyCode: String,
    toCurrencyCode: String,
    conversionValue: Double,
    inverseConversionValue: Double,
    effectiveDate: Date
  ): Box[FXRateRow] = {
    val row = FXRateRow(BankId(bankId), fromCurrencyCode, toCurrencyCode, conversionValue, inverseConversionValue, effectiveDate)
    val now = new Timestamp(effectiveDate.getTime)
    findExact(bankId, fromCurrencyCode, toCurrencyCode) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"""UPDATE mappedfxrate
                  SET mconversionvalue = $conversionValue, minverseconversionvalue = $inverseConversionValue,
                      meffectivedate = $now
                  WHERE mbankid = $bankId AND mfromcurrencycode = $fromCurrencyCode AND mtocurrencycode = $toCurrencyCode"""
              .update.run)
          row
        }
      case None =>
        tryo {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedfxrate
                    (mbankid, mfromcurrencycode, mtocurrencycode, mconversionvalue, minverseconversionvalue, meffectivedate)
                  VALUES ($bankId, $fromCurrencyCode, $toCurrencyCode, $conversionValue, $inverseConversionValue, $now)"""
              .update.run)
          row
        }
    }
  }
}
