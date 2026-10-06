/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.apiproduct.ApiProduct
import code.asset.CurrencyCodes
import code.bulkpayment.BulkPayment
import code.counterpartylimit.CounterpartyLimit
import code.customer.MappedCustomer
import code.fx.{MappedCurrency, MappedFXRate}
import code.metadata.counterparties.MappedCounterparty
import code.model.dataAccess.MappedBankAccount
import code.opencorridorfees.OpenCorridorFeeAccrual
import code.productfee.ProductFee
import code.standingorders.StandingOrder
import code.transaction.MappedTransaction
import code.transaction_types.MappedTransactionType
import code.transactionrequests.{MappedTransactionRequest, MappedTransactionRequestTypeCharge, TransactionRequestReasons}
import code.util.Helper.MdcLoggable
import net.liftweb.db.DB
import net.liftweb.mapper.BaseMetaMapper
import net.liftweb.util.DefaultConnectionIdentifier

import scala.collection.mutable.ListBuffer

/**
 * This migration rewrites every stored currency code in upper case, so `eur` and `Eur` become `EUR`.
 *
 * Currency codes are case-insensitive (code.asset.CurrencyCodes, ideas/ASSET_REGISTRY.md section 4).
 * Since that rule, a code in a request is upper-cased before the endpoint sees it, so new rows are
 * written upper case. Rows written before it can hold a code in another case: `ada`, `lovelace` and
 * `wei` were accepted only in lower case, and a client could store whatever case it sent wherever a
 * currency was not validated. Comparisons in the code ignore case, but database queries that select
 * by currency (the FX rate lookup, for example) match exactly, so such a row would be missed. After
 * this migration every stored code is in the one form the rest of OBP expects.
 *
 * It touches only the columns that hold a currency code, listed below, and only rows whose value is
 * not already trimmed and upper case. Table and column names come from the Mapper definitions. A
 * table or column that does not exist on this database is skipped.
 *
 * `mappedcurrency` keys its rows by the code, so a lower-case row whose upper-case twin is already
 * there is left as it is, and reported, rather than failing on the duplicate key.
 *
 * Currency codes inside stored JSON (the body of a transaction request, for example) are not changed.
 * `lovelace` and `wei` become `LOVELACE` and `WEI`; converting their amounts to ADA and ETH is a
 * separate later step (ideas/ASSET_REGISTRY.md section 7, Part C).
 */
object MigrationOfCurrencyCodesUpperCase extends MdcLoggable {

  private case class CurrencyColumn(table: BaseMetaMapper, column: String, isPrimaryKey: Boolean = false)

  private def currencyColumns: List[CurrencyColumn] = List(
    CurrencyColumn(MappedCurrency, MappedCurrency.mCurrencyCode.dbColumnName, isPrimaryKey = true),
    CurrencyColumn(MappedFXRate, MappedFXRate.mFromCurrencyCode.dbColumnName),
    CurrencyColumn(MappedFXRate, MappedFXRate.mToCurrencyCode.dbColumnName),
    CurrencyColumn(MappedBankAccount, MappedBankAccount.accountCurrency.dbColumnName),
    CurrencyColumn(MappedTransaction, MappedTransaction.currency.dbColumnName),
    CurrencyColumn(MappedTransactionRequest, MappedTransactionRequest.mCharge_Currency.dbColumnName),
    CurrencyColumn(MappedTransactionRequest, MappedTransactionRequest.mBody_Value_Currency.dbColumnName),
    CurrencyColumn(MappedTransactionRequestTypeCharge, MappedTransactionRequestTypeCharge.mChargeCurrency.dbColumnName),
    CurrencyColumn(TransactionRequestReasons, TransactionRequestReasons.Currency.dbColumnName),
    CurrencyColumn(MappedTransactionType, MappedTransactionType.mCustomerFee_Currency.dbColumnName),
    CurrencyColumn(StandingOrder, StandingOrder.AmountCurrency.dbColumnName),
    CurrencyColumn(BulkPayment, BulkPayment.Currency.dbColumnName),
    CurrencyColumn(MappedCustomer, MappedCustomer.mCreditLimitCurrency.dbColumnName),
    CurrencyColumn(MappedCounterparty, MappedCounterparty.mCurrency.dbColumnName),
    CurrencyColumn(CounterpartyLimit, CounterpartyLimit.Currency.dbColumnName),
    CurrencyColumn(ProductFee, ProductFee.Currency.dbColumnName),
    CurrencyColumn(ApiProduct, ApiProduct.MonthlySubscriptionCurrency.dbColumnName),
    CurrencyColumn(OpenCorridorFeeAccrual, OpenCorridorFeeAccrual.Currency.dbColumnName)
  )

  private def columnExists(table: String, column: String): Boolean =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val found = connection.getMetaData.getColumns(null, null, table, column)
      try found.next() finally found.close()
    }

  private def runUpdate(sql: String): Int =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val statement = connection.createStatement()
      try statement.executeUpdate(sql) finally statement.close()
    }

  /**
   * This returns, for the rows matching the condition, each stored value with how many rows hold it,
   * most frequent first. It is read before the update so the log can say what each value became.
   */
  private def valueCounts(table: String, column: String, condition: String): List[(String, Int)] =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val statement = connection.createStatement()
      try {
        val result = statement.executeQuery(
          s"SELECT $column, COUNT(*) FROM $table WHERE $condition GROUP BY $column")
        try {
          val counts = ListBuffer[(String, Int)]()
          while (result.next()) counts += ((result.getString(1), result.getInt(2)))
          // Sorted here rather than in SQL, so values with the same count come in the same order on every database.
          counts.toList.sortBy { case (value, count) => (-count, value) }
        } finally result.close()
      } finally statement.close()
    }

  /** At most this many distinct values are named per column, so one column cannot crowd out the rest. */
  private val MaxValuesNamedPerColumn = 10

  /** This describes the values that changed, e.g. `eur -> EUR x3, 'Gbp ' -> GBP x1`. */
  private def describeChanges(counts: List[(String, Int)]): String = {
    def shown(value: String) = if (value == value.trim) value else s"'$value'"
    val named = counts.take(MaxValuesNamedPerColumn).map { case (value, count) =>
      s"${shown(value)} -> ${CurrencyCodes.normalise(value)} x$count"
    }
    val more = counts.size - MaxValuesNamedPerColumn
    (named ++ (if (more > 0) List(s"and $more other value(s)") else Nil)).mkString(", ")
  }

  /**
   * This upper-cases the stored currency codes in every column listed above and records what it did.
   *
   * The migration log gets one entry per column, in this order, so that if the remark is longer than
   * the log column holds, the part that is cut off is the least important: the columns that changed
   * (with each value and what it became), then rows left alone in `mappedcurrency`, then tables or
   * columns not on this database, then the columns where nothing needed changing. The full text is
   * also written to the server log, one line per column as it is processed.
   */
  def upperCaseEverywhere(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val changedColumns = ListBuffer[String]()
    val leftAloneColumns = ListBuffer[String]()
    val missingColumns = ListBuffer[String]()
    val unchangedColumns = ListBuffer[String]()

    currencyColumns.foreach { currencyColumn =>
      val table = currencyColumn.table.dbTableName.toLowerCase
      val column = currencyColumn.column.toLowerCase
      val qualified = s"$table.$column"
      if (!DbFunction.tableExists(currencyColumn.table)) {
        missingColumns += s"$qualified (no such table)"
        logger.info(s"upperCaseEverywhere says: $qualified skipped, the table does not exist on this database")
      } else if (!columnExists(table, column)) {
        missingColumns += s"$qualified (no such column)"
        logger.info(s"upperCaseEverywhere says: $qualified skipped, the column does not exist on this database")
      } else {
        val needsChange = s"$column <> UPPER(TRIM($column))"
        val twinExists = s"EXISTS (SELECT 1 FROM $table twin WHERE twin.$column = UPPER(TRIM($table.$column)))"
        val toChangeCondition = if (currencyColumn.isPrimaryKey) s"$needsChange AND NOT $twinExists" else needsChange
        val toChange = valueCounts(table, column, toChangeCondition)
        val changed = if (toChange.isEmpty) 0 else runUpdate(s"UPDATE $table SET $column = UPPER(TRIM($column)) WHERE $toChangeCondition")
        val leftAlone = if (currencyColumn.isPrimaryKey) valueCounts(table, column, needsChange) else Nil

        if (changed > 0) {
          val line = s"$qualified: $changed row(s) changed (${describeChanges(toChange)})"
          changedColumns += line
          logger.info(s"upperCaseEverywhere says: $line")
        } else {
          unchangedColumns += qualified
          logger.info(s"upperCaseEverywhere says: $qualified: nothing needed changing")
        }
        if (leftAlone.nonEmpty) {
          val line = s"$qualified: ${leftAlone.map(_._2).sum} row(s) left as they are because the upper-case code is already there (${leftAlone.map(_._1).mkString(", ")})"
          leftAloneColumns += line
          logger.warn(s"upperCaseEverywhere says: $line")
        }
      }
    }

    val sections = List(
      if (changedColumns.isEmpty) "Changed: none" else s"Changed: ${changedColumns.mkString("; ")}",
      if (leftAloneColumns.isEmpty) "" else s"Left alone: ${leftAloneColumns.mkString("; ")}",
      if (missingColumns.isEmpty) "" else s"Not on this database: ${missingColumns.mkString(", ")}",
      if (unchangedColumns.isEmpty) "" else s"Nothing to change: ${unchangedColumns.mkString(", ")}"
    ).filter(_.nonEmpty)
    val comment = s"Upper-cased stored currency codes in ${currencyColumns.size} columns. ${sections.mkString(". ")}."
    saveLog(name, APIUtil.gitCommit, isSuccessful = true, startDate, System.currentTimeMillis(), comment)
    true
  }
}
