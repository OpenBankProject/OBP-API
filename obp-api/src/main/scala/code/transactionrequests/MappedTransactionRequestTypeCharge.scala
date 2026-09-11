package code.transactionrequests

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.TransactionRequestTypeCharge
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * The charge a bank levies for one transaction-request type.
 *
 * The table has no index beyond its primary key, though the only read filters on
 * (mbankid, mtransactionrequesttypeid) and expects at most one row. Nothing enforces that;
 * pre-existing, and the lookup pins id ASC so which row wins is deterministic.
 */
case class MappedTransactionRequestTypeCharge(
  transactionRequestTypeId: String,
  bankId: String,
  chargeCurrency: String,
  chargeAmount: String,
  chargeSummary: String
) extends TransactionRequestTypeCharge

object MappedTransactionRequestTypeCharge {

  private val selectColumns =
    fr"""SELECT mtransactionrequesttypeid, mbankid, mchargecurrency, mchargeamount, mchargesummary
         FROM mappedtransactionrequesttypecharge"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String])

  private def fromRow(row: Row): MappedTransactionRequestTypeCharge = row match {
    case (transactionRequestTypeId, bankId, chargeCurrency, chargeAmount, chargeSummary) =>
      MappedTransactionRequestTypeCharge(transactionRequestTypeId.orNull, bankId.orNull,
        chargeCurrency.orNull, chargeAmount.orNull, chargeSummary.orNull)
  }

  private def query(condition: Fragment): List[MappedTransactionRequestTypeCharge] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(bankId: String, transactionRequestTypeId: String): Box[MappedTransactionRequestTypeCharge] =
    query(fr"""WHERE mbankid = $bankId AND mtransactionrequesttypeid = $transactionRequestTypeId
               ORDER BY id ASC LIMIT 1""").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(bankId: String, transactionRequestTypeId: String, chargeCurrency: String,
             chargeAmount: String, chargeSummary: String): MappedTransactionRequestTypeCharge = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedtransactionrequesttypecharge
            (mbankid, mtransactionrequesttypeid, mchargecurrency, mchargeamount, mchargesummary,
             createdat, updatedat)
            VALUES ($bankId, $transactionRequestTypeId, $chargeCurrency, $chargeAmount,
             $chargeSummary, $now, $now)"""
        .update.run)
    MappedTransactionRequestTypeCharge(transactionRequestTypeId, bankId, chargeCurrency,
      chargeAmount, chargeSummary)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionrequesttypecharge".update.run)
    ()
  }
}

/**
  * This case class is used when there is no data in database and mocked empty data to show it to user.
  */
case class TransactionRequestTypeChargeMock(
                                            mTransactionRequestTypeId: String,
                                            mBankId: String,
                                            mChargeCurrency: String,
                                            mChargeAmount: String,
                                            mChargeSummary: String
                                            ) extends TransactionRequestTypeCharge {

  override def transactionRequestTypeId: String = mTransactionRequestTypeId

  override def bankId: String = mBankId

  override def chargeCurrency: String = mChargeCurrency

  override def chargeAmount: String = mChargeAmount

  override def chargeSummary: String = mChargeSummary
}
