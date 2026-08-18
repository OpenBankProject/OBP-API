package code.productfee

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._
import net.liftweb.util.Helpers

/**
 * A NULL fee amount has to read back as zero, the way MappedDecimal read it.
 *
 * `productfee.amount` is `NUMERIC(34, 2)` with no NOT NULL, and the store bound it as a bare
 * `BigDecimal`. Doobie's Get for a non-nullable type throws NonNullableColumnRead on a SQL NULL and
 * fails the WHOLE query, so one such row does not come back empty - it turns every fee listing for
 * that bank into a 500.
 *
 * Mapper never failed it. MappedDecimal's JDBC setter is `if (isNull) defaultValue`, and its
 * defaultValue is `zero.setScale(scale)` (MappedDecimal.scala:80), so a NULL amount arrived as 0
 * at the column's scale.
 *
 * The reason this survived the sweep that bound every other nullable column in this same store as
 * Option is that check_nullable_column_reads.py could not see the column at all: it read the
 * nullability out of the H2 CREATE TABLE with a regex whose character class had no comma in it, so
 * `NUMERIC(34, 2)` never matched and the column was simply absent from the map it checks against.
 * Four columns of counterpartylimit were invisible for the same reason. Reading the nullability
 * from the Liquibase changelog instead is what surfaced them.
 */
class ProductFeeNullAmountTest extends ServerSetup {

  feature("a productfee row whose amount column is NULL") {

    scenario("reads back as zero rather than failing the whole query") {
      val suffix = Helpers.randomString(12).toLowerCase
      val bankId = "bank_" + suffix
      val productCode = "product_" + suffix

      // Raw SQL on purpose: the store's own insert always binds an amount, so this is the only way
      // to produce the row an older database carries - Schemifier added columns to existing tables
      // with ALTER TABLE ADD COLUMN and no backfill.
      DoobieUtil.runUpdate(
        sql"""INSERT INTO productfee
              (productfeeid, bankid, productcode, name, isactive, moreinfo, currency, amount,
               frequency, type_c)
              VALUES (${"fee_" + suffix}, $bankId, $productCode, ${"fee " + suffix}, true,
               'a fee row whose amount predates the column', 'EUR', NULL, 'MONTHLY', 'FIXED')"""
          .update.run)

      try {
        val fees = ProductFee.findAllByBankIdAndProductCode(bankId, productCode)
        withClue("the listing must not fail on the NULL amount: ") {
          fees should have size 1
        }
        withClue("MappedDecimal read a NULL as zero at the column's scale: ") {
          fees.head.amount should equal(BigDecimal(0).setScale(2))
        }
      } finally {
        DoobieUtil.runUpdate(sql"DELETE FROM productfee WHERE bankid = $bankId".update.run)
      }
    }
  }
}
