package code.users

import code.api.util.DoobieUtil
import code.loginattempts.LoginAttempt
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

import java.sql.{Date => SqlDate, Timestamp}

/**
 * Doobie queries for user search in v6.0.0.
 *
 * Uses a LEFT JOIN between resourceuser, authuser, and mappedbadloginattempt to
 * return all the fields needed for UserJsonV600 in a single SQL round-trip
 * (rather than N+1 per-user AuthUser/MappedBadLoginAttempt lookups).
 *
 * Entitlements and agreements remain separate queries handled by the caller.
 */
object DoobieUserQueries {

  /**
   * Per-endpoint whitelist of sort_by field names accepted by [[getUsers]],
   * mapping the wire name to a hardcoded SQL column expression.
   *
   * Keys must all appear in `SortFields.All` so the global validator passes
   * them through; the map itself is the per-endpoint narrowing. Values are
   * spliced via `Fragment.const` — never accept untrusted strings here, the
   * map constrains that to a known-safe set.
   */
  val SortableColumns: Map[String, String] = Map(
    "user_id"      -> "ru.userid_",
    "username"     -> "ru.name_",
    "email"        -> "ru.email",
    "provider"     -> "ru.provider_",
    "created_date" -> "au.createdat",
    "updated_date" -> "au.updatedat"
  )

  /**
   * Row returned by [[getUsers]]. Fields sourced from AuthUser are Option
   * because external-IdP users may not have an AuthUser row. ResourceUser
   * string columns also use Option since legacy rows may have NULLs even
   * for fields that modern defaults would populate.
   */
  case class UserSearchRow(
    userId: String,
    email: Option[String],
    providerId: Option[String],
    provider: Option[String],
    username: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    isDeleted: Option[Boolean],
    lastMarketingAgreementSignedDate: Option[SqlDate],
    createdDate: Option[Timestamp],
    updatedDate: Option[Timestamp],
    emailValidated: Option[Boolean],
    lastUsedLocale: Option[String],
    isLocked: Boolean
  )

  private val selectColumns: Fragment = fr"""
    SELECT
      ru.userid_                                  AS user_id,
      ru.email                                    AS email,
      ru.providerid                               AS provider_id,
      ru.provider_                                AS provider,
      ru.name_                                    AS username,
      au.firstname                                AS first_name,
      au.lastname                                 AS last_name,
      ru.isdeleted                                AS is_deleted,
      ru.lastmarketingagreementsigneddate         AS last_marketing_agreement_signed_date,
      au.createdat                                AS created_date,
      au.updatedat                                AS updated_date,
      au.validated                                AS email_validated,
      ru.lastusedlocale                           AS last_used_locale,
      CASE WHEN bad_login_attempts.mbadattemptssincelastsuccessorreset IS NOT NULL
                AND bad_login_attempts.mbadattemptssincelastsuccessorreset > """

  private val joinTables: Fragment = fr"""
      FROM resourceuser ru
      LEFT JOIN authuser au                         ON au.user_c            = ru.id
      LEFT JOIN mappedbadloginattempt bad_login_attempts ON bad_login_attempts.provider  = ru.provider_
                                                   AND bad_login_attempts.musername = ru.name_
  """

  /**
   * Find users with optional filters. All filters combine with AND.
   *
   * @param provider     exact match on the identity provider (ResourceUser.provider_)
   * @param username     exact match on the username           (ResourceUser.name_)
   * @param email        exact match on email                  (ResourceUser.email)
   * @param userId       exact match on userId                 (ResourceUser.userid_)
   * @param isDeleted    match on is_deleted                   (default: false)
   * @param lockedStatus "active" / "locked" — filters via MappedBadLoginAttempt
   * @param roleName     require EXISTS an entitlement with this role
   * @param bankId       when roleName is given, scope it to this bank
   * @param sortBy       wire name of the column to sort by (must be a key in
   *                     [[SortableColumns]]); unknown or None falls back to `ru.id`
   * @param sortAsc      true = ASC, false = DESC
   * @param limit        max rows (server cap applied by caller)
   * @param offset       rows to skip
   */
  def getUsers(
    provider: Option[String],
    username: Option[String],
    email: Option[String],
    userId: Option[String],
    isDeleted: Option[Boolean],
    lockedStatus: Option[String],
    roleName: Option[String],
    bankId: Option[String],
    sortBy: Option[String],
    sortAsc: Boolean,
    limit: Int,
    offset: Int
  ): List[UserSearchRow] = {
    val maxBadLogin: Int = LoginAttempt.maxBadLoginAttempts.toInt

    val providerFilter: Fragment = provider.map(v => fr"AND ru.provider_ = $v").getOrElse(Fragment.empty)
    val usernameFilter: Fragment = username.map(v => fr"AND ru.name_     = $v").getOrElse(Fragment.empty)
    val emailFilter:    Fragment = email.map(v => fr"AND ru.email       = $v").getOrElse(Fragment.empty)
    val userIdFilter:   Fragment = userId.map(v => fr"AND ru.userid_    = $v").getOrElse(Fragment.empty)
    val deletedFlag: Boolean     = isDeleted.getOrElse(false)
    val deletedFilter: Fragment  = fr"AND (ru.isdeleted = $deletedFlag OR ru.isdeleted IS NULL AND $deletedFlag = false)"

    val lockedFilter: Fragment = lockedStatus.map(_.toLowerCase) match {
      case Some("locked") =>
        fr"AND bad_login_attempts.mbadattemptssincelastsuccessorreset IS NOT NULL AND bad_login_attempts.mbadattemptssincelastsuccessorreset > $maxBadLogin"
      case Some("active") =>
        fr"AND (bad_login_attempts.mbadattemptssincelastsuccessorreset IS NULL OR bad_login_attempts.mbadattemptssincelastsuccessorreset <= $maxBadLogin)"
      case _ =>
        Fragment.empty
    }

    // role_name/bank_id via EXISTS subquery against mappedentitlement — keeps
    // the outer row count correct (no duplicates when a user has many entitlements).
    val roleFilter: Fragment = roleName match {
      case Some(rn) =>
        val bankClause: Fragment = bankId.map(b => fr"AND ent.mbankid = $b").getOrElse(Fragment.empty)
        fr"AND EXISTS (SELECT 1 FROM mappedentitlement ent WHERE ent.muserid = ru.userid_ AND ent.mrolename = $rn" ++ bankClause ++ fr")"
      case None => Fragment.empty
    }

    // sortBy is looked up in SortableColumns, an internal-only whitelist — never
    // interpolate an arbitrary caller-supplied string into Fragment.const.
    val sortColumn: String = sortBy.flatMap(SortableColumns.get).getOrElse("ru.id")
    val sortDir:    String = if (sortAsc) "ASC" else "DESC"
    val orderBy:   Fragment = Fragment.const(s"ORDER BY $sortColumn $sortDir")

    val query: ConnectionIO[List[UserSearchRow]] =
      (selectColumns ++ fr"$maxBadLogin THEN true ELSE false END AS is_locked" ++
        joinTables ++
        fr"WHERE 1=1" ++
        providerFilter ++
        usernameFilter ++
        emailFilter ++
        userIdFilter ++
        deletedFilter ++
        lockedFilter ++
        roleFilter ++
        orderBy ++
        fr"LIMIT $limit OFFSET $offset")
        .query[UserSearchRow]
        .to[List]

    DoobieUtil.runQuery(query)
  }
}
