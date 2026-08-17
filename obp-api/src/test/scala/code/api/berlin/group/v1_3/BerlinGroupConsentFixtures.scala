package code.api.berlin.group.v1_3

import code.accountholders.AccountHolders
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ConsentAccessAccountsJson, ConsentAccessJson, PostConsentJson}
import code.api.util.APIUtil.OAuth._
import code.api.util.{Consent, ConsentJWT, ConsentView, CustomJsonFormats, JwtUtil}
import code.consent.{ConsentTrait, Consents}
import code.model.TokenType.Access
import code.model.UserX
import code.model.dataAccess.{BankAccountRouting, ResourceUser}
import code.setup.DefaultUsers
import code.token.Tokens
import com.openbankproject.commons.model.User
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.util.JsonAliases
import net.liftweb.mapper.By
import org.json4s.Formats
import net.liftweb.util.Helpers.randomString
import net.liftweb.util.TimeHelpers.TimeSpan

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Fixtures shared by the Berlin Group consent suites: a consent body, a consent lodged without a
 * PSU, and a session shaped like a client_credentials caller's.
 *
 * Extracted here because more than one suite needs them and two copies would have to be kept in
 * agreement -- the client-credentials session in particular encodes a non-obvious fact about how
 * OAuth2 token parsing behaves, and a copy that drifted from it would quietly stop testing the
 * thing it was written for.
 */
trait BerlinGroupConsentFixtures extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  def getNextMonthDate(): String =
    LocalDate.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

  /** One account, addressed by the first IBAN routing in the test data. */
  def bgConsentPostBody(): PostConsentJson = {
    val acountRoutingIban = BankAccountRouting
      .findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString)).head
    PostConsentJson(
      access = ConsentAccessJson(
        accounts = Option(List(ConsentAccessAccountsJson(
          iban = Some(acountRoutingIban.accountRouting.address),
          bban = None,
          pan = None,
          maskedPan = None,
          msisdn = None,
          currency = None,
        ))),
        balances = None,
        transactions = None,
        availableAccounts = None,
        allPsd2 = None
      ),
      recurringIndicator = true,
      validUntil = getNextMonthDate(),
      frequencyPerDay = 4,
      combinedServiceIndicator = Some(false)
    )
  }

  /**
   * The views a stored consent's JWT currently grants.
   *
   * Read off the JWT rather than the AccountAccess rows because the JWT is the consent's scope:
   * applyBerlinGroupConsentRulesCommon re-derives the rows from it on every request, so a test that
   * asserted on the rows would be asserting on a cache of this.
   */
  def consentViewsOf(consentId: String): List[ConsentView] = {
    implicit val formats: Formats = CustomJsonFormats.formats
    val stored = Consents.consentProvider.vend.getConsentByConsentId(consentId)
      .openOrThrowException(s"test consent lookup failed for $consentId")
    JwtUtil.getSignedPayloadAsJson(stored.jsonWebToken)
      .map(JsonAliases.parse(_).extract[ConsentJWT])
      .openOrThrowException(s"test consent JWT could not be read for $consentId")
      .views
  }

  /**
   * The accounts a user holds that Berlin Group can address, as (bank_id, account_id).
   *
   * Berlin Group names accounts by IBAN throughout, so an account with no IBAN routing is not
   * reachable through this API however the consent is worded -- this is the set an
   * "availableAccounts": "allAccounts" consent is expected to resolve to.
   */
  def ibanAddressableAccountsHeldBy(user: User): Set[(String, String)] =
    AccountHolders.accountHolders.vend.getAccountsHeldByUser(user)
      .filter { held =>
        BankAccountRouting.find(
          By(BankAccountRouting.BankId, held.bankId.value),
          By(BankAccountRouting.AccountId, held.accountId.value),
          By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString)
        ).isDefined
      }
      .map(held => (held.bankId.value, held.accountId.value))

  /**
   * An unclaimed (PSU-less) consent lodged under testConsumer, built straight through the provider
   * -- the shape POST /consents leaves behind for a client_credentials caller.
   */
  def createUnclaimedBerlinGroupConsent(): ConsentTrait = {
    val postJsonBody = bgConsentPostBody()
    val validUntilDate = BgSpecValidation.getDate(postJsonBody.validUntil)

    val createdConsent = Consents.consentProvider.vend.createBerlinGroupConsent(
      user = None,
      consumer = Some(testConsumer),
      recurringIndicator = postJsonBody.recurringIndicator,
      validUntil = validUntilDate,
      frequencyPerDay = postJsonBody.frequencyPerDay,
      combinedServiceIndicator = postJsonBody.combinedServiceIndicator.getOrElse(false),
      apiStandard = Some(ConstantsBG.berlinGroupVersion1.apiStandard),
      apiVersion = Some(ConstantsBG.berlinGroupVersion1.apiShortVersion)
    ).openOrThrowException("test consent creation failed")

    val consentJWT = Await.result(
      Consent.createBerlinGroupConsentJWT(
        None,
        postJsonBody,
        createdConsent.secret,
        createdConsent.consentId,
        Some(testConsumer.consumerId.get),
        Some(validUntilDate),
        None
      ),
      10.seconds
    ).openOrThrowException("test consent JWT creation failed")
    Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)

    createdConsent
  }

  // A client_credentials token carries the caller's own client id in `sub`, and OAuth2's
  // getOrCreateResourceUser turns `sub` into idGivenByProvider — so such a token resolves cc.user to
  // an auto-vivified pseudo-user keyed on the consumer's client key rather than leaving it Empty.
  // The OAuth1-signed test harness cannot mint that token, so build the same CallContext shape
  // directly: a user whose idGivenByProvider IS testConsumer's client key, plus an access token for
  // it issued under testConsumer. Signing with this pair gives the endpoint exactly what a
  // client_credentials TPP gives it — cc.user.idGivenByProvider == cc.consumer.key.
  lazy val pseudoUserOfTestConsumer: ResourceUser =
    UserX.findByProviderId(provider = defaultProvider, idGivenByProvider = testConsumer.key.get)
      .map(_.asInstanceOf[ResourceUser])
      .getOrElse {
        UserX.createResourceUser(
          provider = defaultProvider,
          providerId = Some(testConsumer.key.get),
          createdByConsentId = None,
          name = Some(testConsumer.key.get),
          email = Some("pseudo.user.of.test.consumer@example.com"),
          userId = None,
          company = Some("Tesobe GmbH")
        ).openOrThrowException("test pseudo user creation failed")
      }

  lazy val pseudoUserToken = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id.get),
    Some(pseudoUserOfTestConsumer.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException("test pseudo user token creation failed")

  // Same consumer as user1, different token: cc.consumer is testConsumer, cc.user is the pseudo-user.
  lazy val clientCredentialsSession = Some(consumer, Token(pseudoUserToken.key.get, pseudoUserToken.secret.get))
}
