package code.api.UKOpenBanking.v3_1_0

import code.api.util.APIUtil.{DateWithDayFormat, ResourceDoc, UserOrApplication, buildOperationId}
import code.api.util.ErrorMessages.ConsentNotFound
import code.consent.Consents
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * UK Open Banking v3.1 — AIS (Account Information) family.
 *
 * Each endpoint gets two scenarios:
 *   - authenticated  -> the success status the Lift handler currently returns
 *   - unauthenticated -> 401 (every handler calls authenticatedAccess /
 *                         applicationAccess)
 *
 * Endpoints whose authenticated status depends on runtime data (UK consent,
 * existing consent id, account access) are marked DATA-DEPENDENT; their authed
 * assertion is calibrated against the Lift baseline in Phase 1.
 */
class UKOpenBankingV310AisTests extends UKOpenBankingV310ServerSetup {

  object UKOpenBankingV310 extends Tag("UKOpenBankingV310")

  val acc = testAccountId1.value

  // A pending (not yet authorised -- no bound PSU) consent, created by "consumer" (the OAuth1
  // consumer backing user1). For the cross-consumer regression tests: user2 authenticates with a
  // *different* consumer (consumer2, see DefaultUsers), so this simulates a second TPP trying to
  // reach a first TPP's still-pending consent before any PSU has authorised it.
  private def createPendingConsentForConsumer1(): String =
    Consents.consentProvider.vend.saveUKConsent(
      user = None,
      bankId = None,
      accountIds = None,
      consumerId = Some(testConsumer.consumerId.get),
      permissions = List("ReadAccountsBasic"),
      expirationDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      transactionFromDateTime = Some(DateWithDayFormat.parse("2020-01-01")),
      transactionToDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      apiStandard = Some("UKOpenBanking"),
      apiVersion = Some("3.1.0")
    ).openOrThrowException("test consent creation failed").consentId

  // ── AccountAccessApi ───────────────────────────────────────────────
  feature("UKOB v3.1 POST /account-access-consents") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: applicationAccess + ConsentPostBodyUKV310 body parse (201 on success)
      postAuthed("{}", "account-access-consents").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      postUnauthed("{}", "account-access-consents").code should equal(401)
    }
    // See the twin scenario in UKOpenBankingV401AccountInfoTests for why this is pinned: lodging is
    // a client-credentials call with no PSU, and the ResourceDoc default (UserOnly) would 401 it as
    // soon as a client-credentials token stops auto-vivifying a user.
    scenario("ResourceDoc declares UserOrApplication so a PSU-less TPP call is not rejected", UKOpenBankingV310) {
      val docs = ResourceDoc.getResourceDocs(
        List(buildOperationId(ApiVersion.ukOpenBankingV31, "createAccountAccessConsents")))
      docs should not be empty
      docs.foreach(_.authMode should equal(UserOrApplication))
    }
  }
  feature("UKOB v3.1 DELETE /account-access-consents/CONSENT_ID") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: real consent lookup (204 on success)
      deleteAuthed("account-access-consents", "fake-consent-id").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      deleteUnauthed("account-access-consents", "fake-consent-id").code should equal(401)
    }
    // Cross-consumer regression (currently RED): a pending consent (no bound PSU yet) is
    // currently revokable by ANY authenticated party -- the ownership check added to
    // Http4sUKOBv310AccountAccess.deleteAccountAccessConsentsConsentId only guards against a
    // *different bound user*, not a *different consumer*. deleteAuthedAsUser2 authenticates as
    // consumer2 (see DefaultUsers), a different OAuth1 consumer than the one that created this
    // pending consent (testConsumer/consumer). It is refused with 403, and the consent must be
    // left untouched. The refusal says ConsentNotFound rather than naming the consumer: these
    // endpoints answer the same thing for a consent that is not yours and one that does not exist,
    // so that a caller cannot use them to find out which ids are real.
    scenario("authenticated as a different consumer than the creator, pending consent -> 403, and consent is left untouched", UKOpenBankingV310) {
      val consentId = createPendingConsentForConsumer1()
      val response = deleteAuthedAsUser2("account-access-consents", consentId)
      response.code should equal(403)
      // The refusal is still a 403 and still has no side effect, which is what this scenario
      // guards. Only the wording moved: these endpoints now answer ConsentNotFound whatever the
      // reason, so a caller cannot tell "that one is not yours" from "there is no such consent".
      // The specific reason is logged instead.
      response.body.extract[ErrorMessage].message should startWith(ConsentNotFound)

      Consents.consentProvider.vend.getConsentByConsentId(consentId).isDefined should equal(true)
    }
  }
  feature("UKOB v3.1 GET /account-access-consents/CONSENT_ID") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: real consent JWT lookup
      getAuthed("account-access-consents", "fake-consent-id").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("account-access-consents", "fake-consent-id").code should equal(401)
    }
    // Cross-consumer regression (currently RED): same root cause as the DELETE gap above --
    // a pending consent is currently readable by ANY authenticated party. getAuthedAsUser2
    // authenticates as consumer2, a different OAuth1 consumer than the one that created this
    // pending consent. It is refused with 403 ConsentNotFound -- the same answer an id that
    // matches nothing gets, deliberately.
    scenario("authenticated as a different consumer than the creator, pending consent -> 403, not 200", UKOpenBankingV310) {
      val consentId = createPendingConsentForConsumer1()
      val response = getAuthedAsUser2("account-access-consents", consentId)
      response.code should equal(403)
      // The refusal is still a 403 and still has no side effect, which is what this scenario
      // guards. Only the wording moved: these endpoints now answer ConsentNotFound whatever the
      // reason, so a caller cannot tell "that one is not yours" from "there is no such consent".
      // The specific reason is logged instead.
      response.body.extract[ErrorMessage].message should startWith(ConsentNotFound)
    }
  }

  // ── AccountsApi ────────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: checkUKConsent + passesPsd2Aisp
      getAuthed("accounts").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: real account/view lookup
      getAuthed("accounts", acc).code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc).code should equal(401)
    }
  }

  // ── BalancesApi ────────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/balances") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: checkUKConsent + passesPsd2Aisp
      getAuthed("accounts", acc, "balances").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "balances").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /balances") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: checkUKConsent + passesPsd2Aisp (Issue B fix -- kept consistent
      // with v4.0.1's getBalances, see UKOpenBankingV401AccountInfoTests). This OAuth1-signed
      // test request carries no Bearer JWT, so checkUKConsent deterministically 403s here.
      getAuthed("balances").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("balances").code should equal(401)
    }
  }

  // ── BeneficiariesApi ───────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/beneficiaries") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "beneficiaries").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "beneficiaries").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /beneficiaries") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("beneficiaries").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("beneficiaries").code should equal(401)
    }
  }

  // ── DirectDebitsApi ────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/direct-debits") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "direct-debits").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "direct-debits").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /direct-debits") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("direct-debits").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("direct-debits").code should equal(401)
    }
  }

  // ── OffersApi ──────────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/offers") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "offers").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "offers").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /offers") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("offers").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("offers").code should equal(401)
    }
  }

  // ── PartysApi ──────────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/party") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "party").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "party").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /party") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("party").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("party").code should equal(401)
    }
  }

  // ── ProductsApi ────────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/product") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "product").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "product").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /products") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("products").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("products").code should equal(401)
    }
  }

  // ── ScheduledPaymentsApi ───────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/scheduled-payments") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "scheduled-payments").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "scheduled-payments").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /scheduled-payments") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("scheduled-payments").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("scheduled-payments").code should equal(401)
    }
  }

  // ── StandingOrdersApi ──────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/standing-orders") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "standing-orders").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "standing-orders").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /standing-orders") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("standing-orders").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("standing-orders").code should equal(401)
    }
  }

  // ── StatementsApi ──────────────────────────────────────────────────
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/statements") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "statements").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "statements").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/statements/STATEMENT_ID") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "statements", "fake-statement-id").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "statements", "fake-statement-id").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/statements/STATEMENT_ID/file") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "statements", "fake-statement-id", "file").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "statements", "fake-statement-id", "file").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/statements/STATEMENT_ID/transactions") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("accounts", acc, "statements", "fake-statement-id", "transactions").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "statements", "fake-statement-id", "transactions").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /statements") {
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("statements").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("statements").code should equal(401)
    }
  }

  // ── TransactionsApi ────────────────────────────────────────────────
  // Note: GET /accounts/ID/statements/ID/transactions is also registered by
  // TransactionsApi (duplicate of StatementsApi route); Lift serves the first
  // registered (Statements). Tested once above.
  feature("UKOB v3.1 GET /accounts/ACCOUNT_ID/transactions") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: checkUKConsent + passesPsd2Aisp
      getAuthed("accounts", acc, "transactions").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("accounts", acc, "transactions").code should equal(401)
    }
  }
  feature("UKOB v3.1 GET /transactions") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: checkUKConsent + passesPsd2Aisp (Issue B fix -- kept consistent
      // with v4.0.1's getTransactions, see UKOpenBankingV401AccountInfoTests). This OAuth1-signed
      // test request carries no Bearer JWT, so checkUKConsent deterministically 403s here.
      getAuthed("transactions").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("transactions").code should equal(401)
    }
  }

}
