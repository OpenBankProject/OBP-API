package code.asset

import code.api.util.{APIUtil, DoobieUtil}
import code.api.util.migration.MigrationOfCurrencyCodesUpperCase
import code.customer.{CustomerQuery, MappedCustomer}
import code.fx.{DoobieFXRateQueries, FXRateRow}
import code.migration.DoobieMigrationScriptLogProvider
import code.setup.ServerSetup
import doobie.implicits._

/**
 * This class tests the migration that rewrites every stored currency code in upper case
 * (MigrationOfCurrencyCodesUpperCase).
 *
 * Currency codes are case-insensitive, and a code in a request is now upper-cased before it is
 * stored, but rows written earlier can hold `eur` or `Eur`. The scenarios check that such a row is
 * rewritten, that a row already in upper case is not touched, and that in `mappedcurrency`, which is
 * keyed by the code, a lower-case row whose upper-case twin exists is left alone instead of making the
 * migration fail on the duplicate key.
 */
class CurrencyCodesUpperCaseMigrationTest extends ServerSetup {

  private val bankId = s"currency-migration-${APIUtil.generateUUID().take(8)}"

  private def addFxRate(fromCurrencyCode: String, toCurrencyCode: String): FXRateRow =
    DoobieFXRateQueries.createOrUpdate(bankId, fromCurrencyCode, toCurrencyCode, 1.5, 1 / 1.5, new java.util.Date())
      .openOrThrowException(s"the FX rate $fromCurrencyCode -> $toCurrencyCode should have been stored")

  private def fxRatesAtTestBank: List[(String, String)] =
    DoobieFXRateQueries.findAllForBank(bankId).map(rate => (rate.fromCurrencyCode, rate.toCurrencyCode)).sorted

  private def customersAtTestBank: List[MappedCustomer] =
    MappedCustomer.findAll(Some(bankId), None, CustomerQuery(None, None, None, None, None))

  /**
   * Whether this database has the `mappedcurrency` table. The changelog does not create it, so the migration skips
   * the column, and the scenario about it has nothing to run against.
   */
  private def currencyTableExists: Boolean =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'mappedcurrency'".query[Long].unique) > 0

  private def currencyCodesLike(code: String): List[String] =
    DoobieUtil.runQuery(sql"SELECT mcurrencycode FROM mappedcurrency".query[Option[String]].to[List])
      .flatten.filter(_.equalsIgnoreCase(code)).sorted

  /** The table is keyed by the code, and has no store, so the row is written with SQL. */
  private def addCurrency(code: String): Unit = {
    DoobieUtil.runUpdate(
      sql"INSERT INTO mappedcurrency (mcurrencycode, mcurrencyname) VALUES ($code, ${s"Test currency $code"})".update.run)
    ()
  }

  /** The remark the migration recorded under this name. */
  private def recordedRemark(migrationName: String): String =
    DoobieMigrationScriptLogProvider.getMigrationScriptLogs().find(_.name == migrationName)
      .map(_.remark).getOrElse(fail(s"the migration $migrationName should have recorded a log entry"))

  feature("Upper-casing the stored currency codes") {

    scenario("Codes in lower or mixed case are rewritten in upper case, and upper-case ones are kept") {
      Given("FX rates stored with codes in lower, mixed and upper case")
      addFxRate("eur", "usd")
      addFxRate("Gbp", "JPY")
      addFxRate("CHF", "EUR")

      When("the migration runs")
      val migrationName = s"testUpperCaseStoredCurrencyCodes_$bankId"
      MigrationOfCurrencyCodesUpperCase.upperCaseEverywhere(migrationName) shouldBe true

      Then("every code is in upper case and no rate is lost")
      fxRatesAtTestBank shouldBe List(("CHF", "EUR"), ("EUR", "USD"), ("GBP", "JPY"))

      And("the log says, for each column, which values became what")
      val remark = recordedRemark(migrationName)
      remark should include("mappedfxrate.mfromcurrencycode: 2 row(s) changed (Gbp -> GBP x1, eur -> EUR x1)")
      remark should include("mappedfxrate.mtocurrencycode: 1 row(s) changed (usd -> USD x1)")

      When("the migration runs again")
      val secondName = s"testUpperCaseStoredCurrencyCodesAgain_$bankId"
      MigrationOfCurrencyCodesUpperCase.upperCaseEverywhere(secondName) shouldBe true

      Then("the log names the FX rate columns among those with nothing to change")
      val secondRemark = recordedRemark(secondName)
      secondRemark should include("Nothing to change:")
      secondRemark should include("mappedfxrate.mfromcurrencycode")
      secondRemark should not include ("mappedfxrate.mfromcurrencycode: ")
    }

    scenario("A code with spaces around it is trimmed, including when it is already in upper case") {
      Given("credit limit currencies stored as 'EUR ' (upper case, trailing space), ' usd' and 'gbp  '")
      List("EUR " -> "1", " usd" -> "2", "gbp  " -> "3").foreach { case (currency, number) =>
        MappedCustomer.insert(bankIdValue = bankId, email = null, faceImageTime = null, faceImageUrl = null,
          legalName = null, mobileNumber = null, number = s"trim-$number", dateOfBirth = null,
          relationshipStatus = null, dependents = 0, highestEducationAttained = null, employmentStatus = null,
          kycStatus = false, lastOkDate = null, creditRating = null, creditSource = null,
          creditLimitCurrency = currency, creditLimitAmount = null, title = null, branchId = null, nameSuffix = null,
          customerType = null, parentCustomerId = null, isPendingAgent = false, isConfirmedAgent = false)
      }

      When("the migration runs")
      val migrationName = s"testTrimStoredCurrencyCodes_$bankId"
      MigrationOfCurrencyCodesUpperCase.upperCaseEverywhere(migrationName) shouldBe true

      Then("every code is trimmed and in upper case, and no row is lost")
      customersAtTestBank.map(c => c.number -> c.creditLimitCurrency).sorted shouldBe
        List("trim-1" -> "EUR", "trim-2" -> "USD", "trim-3" -> "GBP")
    }

    scenario("A currency row whose upper-case code already exists is left as it is") {
      if (code.api.util.DBUtil.isSqlServer) {
        cancel("SQL Server uses case-insensitive collation for primary keys, preventing duplicate-case rows")
      }
      if (!currencyTableExists) {
        cancel("this database has no mappedcurrency table: the changelog does not create it")
      }
      Given("a currency stored as qqa and as QQA, and another stored only as qqb")
      addCurrency("qqa")
      addCurrency("QQA")
      addCurrency("qqb")

      When("the migration runs")
      val migrationName = s"testUpperCaseStoredCurrencyCodesTwin_$bankId"
      MigrationOfCurrencyCodesUpperCase.upperCaseEverywhere(migrationName) shouldBe true

      Then("qqb is rewritten, and qqa stays beside QQA rather than the migration failing")
      currencyCodesLike("qqb") shouldBe List("QQB")
      currencyCodesLike("qqa") shouldBe List("QQA", "qqa")

      And("the log names both")
      val remark = recordedRemark(migrationName)
      remark should include("mappedcurrency.mcurrencycode: 1 row(s) changed (qqb -> QQB x1)")
      remark should include("mappedcurrency.mcurrencycode: 1 row(s) left as they are because the upper-case code is already there (qqa)")
    }
  }

  override def afterAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedfxrate WHERE mbankid = $bankId".update.run)
    customersAtTestBank.foreach(c => MappedCustomer.deleteByCustomerId(c.customerId))
    if (currencyTableExists)
      DoobieUtil.runUpdate(sql"DELETE FROM mappedcurrency WHERE LOWER(mcurrencycode) IN ('qqa', 'qqb')".update.run)
    super.afterAll()
  }
}
