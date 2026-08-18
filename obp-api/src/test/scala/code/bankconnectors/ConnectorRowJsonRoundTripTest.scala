package code.bankconnectors

import code.api.util.OptionalFieldSerializer
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ReflectUtils
import org.json4s.Formats
import org.json4s.jvalue2extractable
import net.liftweb.common.Full

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Rows that a connector method returns have to survive ConnectorUtils.proxyConnector.
 *
 * The proxy serializes whatever LocalMappedConnector returned - through
 * OptionalFieldSerializer.toIgnoreFieldJson, which is Extraction.decompose underneath - and
 * re-extracts it as the matching InBound DTO's payload type. json4s decompose writes a case class's
 * CONSTRUCTOR PARAMETERS, not its trait accessors, so a row whose parameters are named after its
 * columns produces JSON the commons type cannot read: required fields come back missing and
 * extraction throws.
 *
 * MappedBank hit this during the Doobie migration (bankIdValue vs bankId) and was fixed by naming
 * the parameters after the trait. ProxyConnectorTest pins that one method, getBanks. These
 * scenarios extend the guarantee to the other rows that cross the same boundary, so that the next
 * row to be rewritten is caught by a test rather than by a connector-mode deployment.
 */
class ConnectorRowJsonRoundTripTest extends code.setup.ServerSetupWithTestData {

  override implicit val formats: Formats = LocalMappedConnector.formats

  /** Exactly what deleteIgnoreFields does: decompose, then extract as the commons payload type. */
  private def roundTrip[T <: AnyRef : Manifest](row: AnyRef): T = {
    val json = OptionalFieldSerializer.toIgnoreFieldJson(row, ReflectUtils.getType(row))
    json.extract[T]
  }

  feature("a row a connector returns re-extracts as its commons type") {

    scenario("a stored balance") {
      val row = code.bankaccountbalance.BankAccountBalance(
        balanceId = BalanceId("b1"),
        bankId = BankId("bank1"),
        accountId = AccountId("account1"),
        balanceType = "closingBooked",
        balanceAmount = BigDecimal("123.45"),
        referenceDate = Some("2026-08-18"),
        lastChangeDateTime = Some(new java.util.Date()),
        balanceAmountSmallestUnit = 12345L,
        currency = "EUR")

      val commons = roundTrip[BankAccountBalanceTraitCommons](row)
      commons.bankId.value should equal("bank1")
      commons.accountId.value should equal("account1")
      commons.balanceId.value should equal("b1")
      commons.balanceAmount should equal(BigDecimal("123.45"))
      commons.referenceDate should equal(Some("2026-08-18"))
    }

    scenario("an account, end to end through the registered proxy connector") {
      // ProxyConnectorTest pins getBanks this way; accounts are the higher-traffic type and reach
      // the same InBound extraction, so whatever survives for banks has to survive here too.
      val bankId = BankId("proxy-round-trip-bank")
      val accountId = AccountId("proxy-round-trip-account")
      createBank(bankId.value)
      createAccount(bankId, accountId, "EUR")

      val proxy = Connector.getConnectorInstance("proxy")
      val viaProxy = Await.result(
        proxy.checkBankAccountExists(bankId, accountId, None), 30.seconds)._1

      viaProxy shouldBe a[Full[_]]
      val account = viaProxy.openOrThrowException("the account must survive the proxy round trip")
      account.bankId should equal(bankId)
      account.accountId should equal(accountId)
      account.currency should equal("EUR")
    }
  }
}
