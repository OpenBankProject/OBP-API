package code.api.UKOpenBanking.v3_1_0

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

  // ── AccountAccessApi ───────────────────────────────────────────────
  feature("UKOB v3.1 POST /account-access-consents") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: applicationAccess + ConsentPostBodyUKV310 body parse (201 on success)
      postAuthed("{}", "account-access-consents").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      postUnauthed("{}", "account-access-consents").code should equal(401)
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
  }
  feature("UKOB v3.1 GET /account-access-consents/CONSENT_ID") {
    scenario("authenticated", UKOpenBankingV310) {
      // DATA-DEPENDENT: real consent JWT lookup
      getAuthed("account-access-consents", "fake-consent-id").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("account-access-consents", "fake-consent-id").code should equal(401)
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
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("balances").code should equal(200)
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
    scenario("authenticated -> 200", UKOpenBankingV310) {
      getAuthed("transactions").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV310) {
      getUnauthed("transactions").code should equal(401)
    }
  }

}
