package code.asset

import code.api.util.APIUtil
import code.api.util.migration.MigrationOfCurrencyCodesUpperCase
import code.fx.{MappedCurrency, MappedFXRate}
import code.migration.MigrationScriptLog
import code.setup.ServerSetup
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier

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

  private def addFxRate(fromCurrencyCode: String, toCurrencyCode: String): MappedFXRate =
    MappedFXRate.create.mBankId(bankId).mFromCurrencyCode(fromCurrencyCode).mToCurrencyCode(toCurrencyCode)
      .mConversionValue(1.5).mInverseConversionValue(1 / 1.5).mEffectiveDate(new java.util.Date()).saveMe()

  private def fxRatesAtTestBank: List[(String, String)] =
    MappedFXRate.findAll(By(MappedFXRate.mBankId, bankId)).map(rate => (rate.fromCurrencyCode, rate.toCurrencyCode)).sorted

  private def currencyCodesLike(code: String): List[String] =
    MappedCurrency.findAll().map(_.currencyCode).filter(_.equalsIgnoreCase(code)).sorted

  /** Mapper does not let a string primary key be set, so the row is written with SQL. */
  private def addCurrency(code: String): Unit =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val statement = connection.prepareStatement(
        s"INSERT INTO ${MappedCurrency.dbTableName} (${MappedCurrency.mCurrencyCode.dbColumnName}, ${MappedCurrency.mCurrencyName.dbColumnName}) VALUES (?, ?)")
      try {
        statement.setString(1, code)
        statement.setString(2, s"Test currency $code")
        statement.executeUpdate()
      } finally statement.close()
    }

  /** The remark the migration recorded under this name. */
  private def recordedRemark(migrationName: String): String =
    MigrationScriptLog.find(By(MigrationScriptLog.Name, migrationName))
      .map(_.remark).openOrThrowException(s"the migration $migrationName should have recorded a log entry")

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

    scenario("A currency row whose upper-case code already exists is left as it is") {
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
    MappedFXRate.findAll(By(MappedFXRate.mBankId, bankId)).foreach(_.delete_!)
    MappedCurrency.findAll().filter(currency => Set("qqa", "qqb").contains(currency.currencyCode.toLowerCase)).foreach(_.delete_!)
    super.afterAll()
  }
}
