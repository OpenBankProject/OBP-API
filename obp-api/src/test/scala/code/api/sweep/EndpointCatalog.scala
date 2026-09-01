package code.api.sweep

// EndpointAuthMode and its four cases are members of the APIUtil object, not of the package.
import code.api.util.APIUtil.{ResourceDoc, UserOnly}
import code.api.util.ErrorMessages.$AuthenticatedUserIsRequired
import code.api.util.ApiTag

/**
 * The one place that answers "what endpoints exist, and what does each one claim about auth".
 *
 * Every sweep in this package reads the catalog from here rather than assembling its own, so
 * that the coverage identity in SweepCoverageTest is checkable: asserted + skipped == catalog,
 * with no third bucket anyone can quietly slip an endpoint into.
 *
 * Three things about the source data are easy to get wrong, and all three are load-bearing:
 *
 * 1. The docs live on the Http4s objects, NOT on APIMethods*. Every APIMethods{121..600}.scala
 *    is now a stub whose entire body is `val ImplementationsX = Http4sX.ImplementationsX` — the
 *    Lift registrations below it are commented out. Reading those files for a catalog finds
 *    nothing. Http4s700.allResourceDocs is the aggregate: every version's docs, deduplicated by
 *    (requestUrl, requestVerb) keeping the newest, which is exactly the set a caller can reach.
 *
 * 2. "Needs authentication" is derived, not declared. There is no flag on ResourceDoc; the
 *    middleware's own predicate is
 *      errorResponseBodies.contains($AuthenticatedUserIsRequired) || roles.exists(_.nonEmpty)
 *    (ResourceDocMiddleware.needsAuthentication). We reproduce it here rather than approximate it.
 *
 * 3. That predicate has to be evaluated on the CONSTRUCTED ResourceDoc, never on the source text.
 *    The constructor rewrites errorResponseBodies: it appends AuthenticatedUserIsRequired and
 *    UserHasMissingRoles when roles are present, and adds/removes AuthenticatedUserIsRequired
 *    based on the description. Several docs also compute their error list from a prop — e.g.
 *    getApiProduct branches on getApiProductsIsPublic — so the answer depends on the props in
 *    force at the moment the sweep runs, which is another reason to ask the object and not a grep.
 */
object EndpointCatalog {

  /** Every endpoint a caller can reach, newest version of each (url, verb). */
  def all: List[ResourceDoc] = code.api.v7_0_0.Http4s700.allResourceDocs.toList

  /** The middleware's own rule, reproduced. See ResourceDocMiddleware.needsAuthentication. */
  def needsAuthentication(doc: ResourceDoc): Boolean =
    doc.errorResponseBodies.contains($AuthenticatedUserIsRequired) || doc.roles.exists(_.nonEmpty)

  def isRoleGated(doc: ResourceDoc): Boolean = doc.roles.exists(_.nonEmpty)

  /**
   * Why an endpoint is not swept. Every exclusion is one of these — a sweep may not invent a
   * reason inline, because SweepCoverageTest counts these and nothing else.
   */
  sealed abstract class SkipReason(val why: String)
  case object DynamicDoc extends SkipReason(
    "tagged apiTagDynamic: created per-database, so its presence is machine state, not contract")
  case object NonUserAuthMode extends SkipReason(
    "authMode is not UserOnly: an anonymous call may legitimately not be 401 " +
    "(ApplicationOnly drops AuthenticatedUserIsRequired entirely). EndpointAuthModeTest covers these.")
  case object AutoValidateRolesOff extends SkipReason(
    "disableAutoValidateRoles(): roles stay in the doc for the catalog but the framework does " +
    "not enforce them, so asserting 403 would assert something no code promises")

  /** Skip reason, or None when the endpoint is in scope for the auth sweep. */
  def skipReason(doc: ResourceDoc): Option[SkipReason] =
    if (doc.tags.contains(ApiTag.apiTagDynamic)) Some(DynamicDoc)
    else if (doc.authMode != UserOnly) Some(NonUserAuthMode)
    else None

  /** Skip reason for the role dimension specifically — a superset of skipReason. */
  def roleSkipReason(doc: ResourceDoc): Option[SkipReason] =
    skipReason(doc).orElse(
      if (!doc.isAutoValidateRoles) Some(AutoValidateRolesOff) else None)

  /**
   * Not every ALL_CAPS segment in a requestUrl is a placeholder. OBP serves real literals in
   * that shape — `/transaction-request-types/SANDBOX_TAN/`, `/my/consents/EMAIL` — and
   * substituting those produces a URL that routes nowhere, which reads as a 404/400 "auth
   * failure" that is entirely the sweep's own doing. The first run of AuthSweepTest hit exactly
   * that on nine endpoints across v2.1.0 and v3.1.0.
   *
   * The production rule lives in ResourceDocMatcher.isTemplateVariable, which consults a private
   * `literalAllCapsSegments` set. Copying that set here would give us a second copy to keep in
   * sync, and a stale copy fails in the direction that is hardest to notice — a literal newly
   * added there would be substituted here and the sweep would quietly stop covering that path.
   *
   * So this asks the opposite question: rather than "is it a literal", "do I have a value for
   * it". A segment is substituted only when its NAME says it is an id or a code. That happens to
   * separate the two sets exactly, including the pairs that differ by suffix alone — CARD and
   * ACCOUNT are literals, CARD_ID and ACCOUNT_ID are placeholders — and it needs no maintenance
   * when a new literal appears, because an unrecognised segment is left alone by default.
   */
  /** True when the URL carries at least one segment concretePath would substitute. */
  def hasPlaceholder(doc: ResourceDoc): Boolean = doc.requestUrl.split("/").exists(isPlaceholder)

  private def isPlaceholder(seg: String): Boolean =
    seg.nonEmpty &&
      seg == seg.toUpperCase &&
      seg.forall(c => c.isLetter || c == '_' || c.isDigit) &&
      // `ID` rather than `_ID`: the UK Open Banking paths spell them without the separator
      // (CONSENTID, ACCOUNTID, BASKETID, DOMESTICPAYMENTID …), and leaving those literal sent
      // the string "CONSENTID" to the server as though it were an id.
      (seg.endsWith("ID") || seg.endsWith("_CODE") || seg.endsWith("_NAME") ||
       seg == "PROVIDER" || seg == "USERNAME" || seg == "USER_EMAIL" ||
       // Named individually because none of the three ends in ID/_CODE/_NAME, yet each is a
       // genuine enumerated-value placeholder a live endpoint validates inline, not a literal:
       // Http4sBGv2PIS's payment-service branches guard on
       // Set("payments","bulk-payments","periodic-payments").contains(paymentService), and
       // Http4s310/Http4s400's auth-context-updates and consent SCA branches guard on
       // List(StrongCustomerAuthentication.SMS, EMAIL[, IMPLICIT]).contains(scaMethod). Left as
       // the literal strings "PAYMENT_SERVICE"/"SCA_METHOD", both guards fail and the sweep
       // reports the resulting 404/400 as the endpoint's own defect -- confirmed reproducing
       // today for SCA_METHOD via OBPv5.0.0-createUserAuthContextUpdateRequest.
       // PAYMENT_PRODUCT is not independently validated anywhere it appears, but is named here
       // for the same reason PROVIDER/USERNAME/USER_EMAIL are: it identifies a Berlin Group
       // payment product, not a literal segment, and treating it as one just because nothing
       // currently checks its value the way PAYMENT_SERVICE and SCA_METHOD are checked would be
       // an accident of the present call sites, not the actual contract of the segment.
       seg == "PAYMENT_SERVICE" || seg == "PAYMENT_PRODUCT" || seg == "SCA_METHOD")

  // Checked against Http4sSupport's literalAllCapsSegments: not one of the sixteen ends in ID,
  // _CODE or _NAME, so the rule above separates the two sets cleanly.
  //
  // CARDANO, MOBILE_WALLET and ETH_SEND_TRANSACTION are the remaining literals this list does
  // not carry, and they stay verbatim on purpose -- substituting over them would route to the
  // wrong case entirely (or none), which is the coverage hole this whole heuristic exists to
  // avoid on the literal side.

  /**
   * The concrete path to call.
   *
   * `entities` supplies values for the identifiers the caller wants resolvable. Anything not
   * named there gets a well-formed value that does not exist.
   *
   * The distinction matters for the 403 assertion. A ResourceDoc that declares BankNotFound and
   * carries BANK_ID is validated for bank existence BEFORE its roles are checked
   * (APIUtil.ResourceDoc's isNeedCheckBank), so a nonexistent bank answers 404 and the role gate
   * is never reached. The sweep's first run read those 404s as missing 403s across some thirty
   * endpoints; they were the entity check doing its job. Passing a real bank id is what makes
   * the assertion actually about roles.
   */
  def concretePath(doc: ResourceDoc, entities: Map[String, String] = Map.empty): String = {
    val segments = doc.requestUrl.split("/").map { seg =>
      if (isPlaceholder(seg)) entities.getOrElse(seg, defaultValue(seg)) else seg
    }
    "/obp/" + doc.implementedInApiVersion.apiShortVersion + segments.mkString("/")
  }

  /** Well-formed, and deliberately not present. */
  private def defaultValue(seg: String): String = seg match {
    case "ACCOUNT_ID" | "USER_ID" => "00000000-0000-0000-0000-000000000000"
    // GRANT_VIEW_ID alongside VIEW_ID, and for the same reason a real bank id is passed for the
    // role assertion: an endpoint that resolves the view before it checks roles answers 404 for a
    // view that does not exist, and the role gate never runs. Measured on
    // createTransactionRequestFreeForm, which the sweep reported as "expected 403, got 500" --
    // two defects stacked, the endpoint's raw throw AND this placeholder never resolving.
    case "VIEW_ID" | "GRANT_VIEW_ID" => "owner"
    case "USER_EMAIL"             => "sweep-no-such-user@example.com"
    // Enumerated values a live endpoint validates inline (see isPlaceholder) -- a well-formed
    // but nonexistent value here would still fail that inline check, same as an id that does not
    // exist fails a lookup, so these get one of the endpoint's own accepted values instead.
    case "PAYMENT_SERVICE"        => "payments"
    case "PAYMENT_PRODUCT"        => "sepa-credit-transfers"
    case "SCA_METHOD"             => "SMS"
    case _                        => "sweep-no-such-" + seg.toLowerCase.replace('_', '-')
  }
}
