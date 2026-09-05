package code.TransactionTypes

import code.TransactionTypes.TransactionType.TransactionType
import code.api.util.{DoobieUtil, ErrorMessages}
import code.api.v2_0_0.TransactionTypeJsonV200
import com.openbankproject.commons.model.{AmountOfMoney, BankId, TransactionTypeId}
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

/**
 * Doobie implementation of the transaction-type store, replacing the Lift MappedTransactionType
 * entity. Written rather than ported - the reference branch never migrated this table.
 *
 * createOrUpdate is an upsert keyed on the transaction type id, matching the Mapper version: it
 * looks the id up and either rewrites every column or inserts. That is not just a nicety - the
 * table carries UniqueIndex(mTransactionTypeId) and UniqueIndex(mBankId, mShortCode), so an
 * unconditional insert would collide on the second call. The two error messages are kept
 * distinct because the endpoint surfaces them.
 *
 * The fee is two columns: a currency string and an amount held as a Long, rendered back as a
 * string by toTransactionType. That conversion is preserved here.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on an autoCommit=false pool, so the write would be rolled back on return.
 */
object DoobieTransactionTypeProvider extends TransactionTypeProvider {

  private def rowToTransactionType(r: (String, String, String, String, String, String, Long)): TransactionType =
    TransactionType(
      id = TransactionTypeId(r._1),
      bankId = BankId(r._2),
      shortCode = r._3,
      summary = r._4,
      description = r._5,
      charge = AmountOfMoney(currency = r._6, amount = r._7.toString))

  private val selectCols: Fragment =
    fr"""SELECT mtransactiontypeid, mbankid, mshortcode, msummary, mdescription,
                mcustomerfee_currency, mcustomerfee_amount
         FROM mappedtransactiontype"""

  override protected def getTransactionTypeFromProvider(transactionTypeId: TransactionTypeId): Option[TransactionType] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mtransactiontypeid = ${transactionTypeId.value} LIMIT 1")
        .query[(String, String, String, String, String, String, Long)].option
    ).map(rowToTransactionType)

  override protected def getTransactionTypesForBankFromProvider(bankId: BankId): Some[List[TransactionType]] =
    Some(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${bankId.value}")
          .query[(String, String, String, String, String, String, Long)].to[List]
      ).map(rowToTransactionType))

  override protected def createOrUpdateTransactionTypeAtProvider(t: TransactionTypeJsonV200): Box[TransactionType] = {
    val id = t.id.toString
    val amount = t.charge.amount.toString.toLong
    val currency = t.charge.currency.toString

    val exists = DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM mappedtransactiontype WHERE mtransactiontypeid = $id".query[Int].unique) > 0

    val result =
      if (exists) {
        tryo {
          DoobieUtil.runUpdate(
            sql"""UPDATE mappedtransactiontype
                  SET mbankid = ${t.bank_id}, mshortcode = ${t.short_code}, msummary = ${t.summary},
                      mdescription = ${t.description}, mcustomerfee_currency = $currency,
                      mcustomerfee_amount = $amount
                  WHERE mtransactiontypeid = $id""".update.run)
        } ?~! ErrorMessages.CreateTransactionTypeUpdateError
      } else {
        tryo {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedtransactiontype
                    (mtransactiontypeid, mbankid, mshortcode, msummary, mdescription,
                     mcustomerfee_currency, mcustomerfee_amount)
                  VALUES ($id, ${t.bank_id}, ${t.short_code}, ${t.summary}, ${t.description},
                          $currency, $amount)""".update.run)
        } ?~! ErrorMessages.CreateTransactionTypeInsertError
      }

    result.map(_ =>
      TransactionType(
        id = TransactionTypeId(id),
        bankId = BankId(t.bank_id),
        shortCode = t.short_code,
        summary = t.summary,
        description = t.description,
        charge = AmountOfMoney(currency, amount.toString)))
  }
}
