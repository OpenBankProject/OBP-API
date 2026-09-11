package code.fx

import java.util.Date

import code.bankconnectors.LocalMappedConnector
import code.setup.ServerSetup
import com.openbankproject.commons.model.BankId

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Characterization of FX rate storage, written before the implementation moves to Doobie.
 *
 * ExchangeRateTest only covers the GET path through NewStyle.getExchangeRate's fallback, which
 * constructs a rate value without persisting it - nothing pins createOrUpdateFXRate, the actual
 * write path, or getCurrentFxRate's reverse-order fallback.
 *
 * There is no unique index on this table (only plain indexes on the two currency-code columns),
 * so createOrUpdateFXRate's find-then-write is the only thing standing between a repeated call
 * and a duplicate row.
 */
class FXRateProviderTest extends ServerSetup {

  private val bankId = "fx-rate-test-bank"

  private def createOrUpdate(from: String, to: String, value: Double, inverse: Double): Unit = {
    Await.result(
      LocalMappedConnector.createOrUpdateFXRate(bankId, from, to, value, inverse, new Date(), None),
      10.seconds)
    ()
  }

  Feature("FX rate storage") {

    Scenario("create then read back in the same direction") {
      createOrUpdate("EUR", "USD", 1.1, 0.9)

      val found = LocalMappedConnector.getCurrentFxRate(BankId(bankId), "EUR", "USD", None)
        .openOrThrowException("just created")
      found.conversionValue should equal(1.1)
      found.inverseConversionValue should equal(0.9)
    }

    Scenario("read back resolves the reverse direction too") {
      createOrUpdate("GBP", "JPY", 190.0, 0.00526)

      val reverse = LocalMappedConnector.getCurrentFxRate(BankId(bankId), "JPY", "GBP", None)
        .openOrThrowException("found via reverse lookup")
      reverse.fromCurrencyCode should equal("GBP")
      reverse.toCurrencyCode should equal("JPY")
    }

    Scenario("a repeated create for the same triple updates in place rather than adding a row") {
      createOrUpdate("AUD", "CAD", 1.0, 1.0)
      createOrUpdate("AUD", "CAD", 1.5, 0.6667)

      val found = LocalMappedConnector.getCurrentFxRate(BankId(bankId), "AUD", "CAD", None)
        .openOrThrowException("updated")
      found.conversionValue should equal(1.5)
    }

    Scenario("getCurrentCurrencies lists both sides of every rate for the bank") {
      createOrUpdate("SEK", "NOK", 0.95, 1.05)

      val currencies = Await.result(LocalMappedConnector.getCurrentCurrencies(BankId(bankId), None), 10.seconds)
        ._1.openOrThrowException("listed")
      currencies should contain("SEK")
      currencies should contain("NOK")
    }

    Scenario("an unknown pair is not found") {
      LocalMappedConnector.getCurrentFxRate(BankId(bankId), "XXX", "YYY", None).isDefined should equal(false)
    }
  }
}
