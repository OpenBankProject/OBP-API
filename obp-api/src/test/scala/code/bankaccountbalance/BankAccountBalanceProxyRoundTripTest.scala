package code.bankaccountbalance

import code.bankconnectors.LocalMappedConnector
import code.setup.ServerSetup
import com.openbankproject.commons.model.{AccountId, BalanceId, BankAccountBalanceTraitCommons, BankId}
import org.json4s.{Extraction, Formats}
import org.json4s.jvalue2extractable

/**
 * A row a connector method returns has to survive ConnectorUtils.proxyConnector's JSON round trip.
 *
 * The proxy serializes whatever LocalMappedConnector returned and re-extracts it as the matching
 * InBound DTO - for balances that is InBoundGetBankAccountBalancesByAccountId, whose data is a
 * List[BankAccountBalanceTraitCommons]. Extraction matches by FIELD NAME, so a row whose fields are
 * named after its columns rather than after the trait comes back with every field null. MappedBank
 * failed exactly this way (bankIdValue vs bankId) and the fix was to name the fields after the
 * trait; this test states the same requirement for balances, which cross the same boundary.
 */
class BankAccountBalanceProxyRoundTripTest extends ServerSetup {

  override implicit val formats: Formats = LocalMappedConnector.formats

  feature("a stored balance crossing the connector boundary") {

    scenario("re-extracts as BankAccountBalanceTraitCommons with its values intact") {
      val row = BankAccountBalance(
        balanceId = BalanceId("balance-round-trip"),
        bankId = BankId("bank-round-trip"),
        accountId = AccountId("account-round-trip"),
        balanceType = "closingBooked",
        balanceAmount = BigDecimal("123.45"),
        referenceDate = Some("2026-08-18"),
        lastChangeDateTime = Some(new java.util.Date()),
        balanceAmountSmallestUnit = 12345L,
        currency = "EUR")

      // Same two steps the proxy performs: decompose, then extract as the commons type.
      val serialized = Extraction.decompose(row)
      val commons = serialized.extract[BankAccountBalanceTraitCommons]

      commons.bankId.value should equal("bank-round-trip")
      commons.accountId.value should equal("account-round-trip")
      commons.balanceId.value should equal("balance-round-trip")
      commons.balanceType should equal("closingBooked")
      commons.balanceAmount should equal(BigDecimal("123.45"))
      commons.referenceDate should equal(Some("2026-08-18"))
      commons.lastChangeDateTime.isDefined should equal(true)
    }
  }
}
