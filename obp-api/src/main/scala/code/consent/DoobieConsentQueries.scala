package code.consent

import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import code.api.util.DoobieUtil

import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

/**
 * Doobie-based query implementations for consents.
 *
 * Reads from the v_consent SQL view which maps mappedconsent column names
 * to JSON-friendly names matching ConsentInfoJsonV510.
 *
 * The jwt_payload field is pre-computed and stored in the DB when the JWT is set,
 * avoiding expensive JWT decoding on every read.
 */
object DoobieConsentQueries {

  /**
   * Row type matching the v_consent view columns.
   * Fields align with ConsentInfoJsonV510.
   */
  case class ConsentRow(
    consentReferenceId: String,
    consentId: String,
    createdByUserId: String,
    consumerId: Option[String],
    status: String,
    jwt: Option[String],
    consentRequestId: Option[String],
    apiStandard: Option[String],
    apiVersion: Option[String],
    lastActionDate: Option[Timestamp],
    lastUsageDate: Option[Timestamp],
    createdDate: Option[Timestamp],
    note: Option[String],
    frequencyPerDay: Option[Int],
    usesSoFarTodayCounter: Option[Int],
    jwtPayload: Option[String],
    jwtExpiresAt: Option[Timestamp]
  )

  /**
   * Get consents for a user with DB-level pagination, filtering, and sorting.
   *
   * @param userId The user ID to filter by
   * @param status Optional status filter (comma-separated values)
   * @param limit Max rows to return
   * @param offset Rows to skip
   * @param sortField Column to sort by (created_date, status, consumer_id)
   * @param sortDirection "asc" or "desc"
   * @return (list of consent rows, total count)
   */
  def getConsentsByUser(
    userId: String,
    status: Option[String],
    limit: Int,
    offset: Int,
    sortField: String,
    sortDirection: String
  ): List[ConsentRow] = {
    val query = buildGetConsentsQuery(userId, status, limit, offset, sortField, sortDirection)
    DoobieUtil.runQuery(query)
  }

  /**
   * Get all consents for a user, ordered by created_date desc.
   * Used by simpler endpoints that don't need pagination params.
   */
  def getAllConsentsByUser(userId: String): List[ConsentRow] = {
    val query =
      fr"""SELECT consent_reference_id, consent_id, created_by_user_id, consumer_id,
           status, jwt, consent_request_id, api_standard, api_version,
           last_action_date, last_usage_date, created_date,
           note, frequency_per_day, uses_so_far_today_counter, jwt_payload, jwt_expires_at
           FROM v_consent
           WHERE created_by_user_id = $userId
           ORDER BY created_date DESC, api_standard DESC"""
        .query[ConsentRow].to[List]
    DoobieUtil.runQuery(query)
  }

  /**
   * Get consents with full filtering for management endpoints.
   *
   * @param userId Optional user ID filter
   * @param consumerId Optional consumer ID filter
   * @param consentId Optional consent ID filter
   * @param status Optional status filter (comma-separated)
   * @param limit Max rows
   * @param offset Rows to skip
   * @param sortField Sort column
   * @param sortDirection "asc" or "desc"
   * @return (list of consent rows, total count)
   */
  def getConsentsFiltered(
    userId: Option[String] = None,
    consumerId: Option[String] = None,
    consentId: Option[String] = None,
    status: Option[String] = None,
    limit: Int = 50,
    offset: Int = 0,
    sortField: String = "created_date",
    sortDirection: String = "desc"
  ): List[ConsentRow] = {
    val query = buildFilteredQuery(userId, consumerId, consentId, status, limit, offset, sortField, sortDirection)
    DoobieUtil.runQuery(query)
  }

  private val selectColumns =
    fr"""SELECT consent_reference_id, consent_id, created_by_user_id, consumer_id,
         status, jwt, consent_request_id, api_standard, api_version,
         last_action_date, last_usage_date, created_date,
         note, frequency_per_day, uses_so_far_today_counter, jwt_payload, jwt_expires_at
         FROM v_consent"""

  private def buildStatusCondition(status: Option[String]): Fragment = status match {
    case Some(s) =>
      val statuses = s.split(",").toList.map(_.trim)
      val distinctStatuses = statuses.distinct.flatMap(v => List(v.toLowerCase, v.toUpperCase)).distinct
      val placeholders = distinctStatuses.map(v => fr"$v").reduce((a, b) => a ++ fr"," ++ b)
      fr"AND status IN (" ++ placeholders ++ fr")"
    case None => fr""
  }

  private def buildOrderBy(sortField: String, sortDirection: String): Fragment =
    (sortField, sortDirection.toLowerCase) match {
      case ("status", "asc")        => fr"ORDER BY status ASC"
      case ("status", _)            => fr"ORDER BY status DESC"
      case ("consumer_id", "asc")   => fr"ORDER BY consumer_id ASC"
      case ("consumer_id", _)       => fr"ORDER BY consumer_id DESC"
      case ("created_date", "asc")  => fr"ORDER BY created_date ASC"
      case (_, _)                   => fr"ORDER BY created_date DESC"
    }

  private def buildGetConsentsQuery(
    userId: String,
    status: Option[String],
    limit: Int,
    offset: Int,
    sortField: String,
    sortDirection: String
  ): ConnectionIO[List[ConsentRow]] = {
    val statusCond = buildStatusCondition(status)
    val orderBy = buildOrderBy(sortField, sortDirection)

    val whereClause = fr"WHERE created_by_user_id = $userId " ++ statusCond
    val dataQuery = selectColumns ++ fr" " ++ whereClause ++ fr" " ++ orderBy ++ fr" LIMIT $limit OFFSET $offset"

    dataQuery.query[ConsentRow].to[List]
  }

  private def buildFilteredQuery(
    userId: Option[String],
    consumerId: Option[String],
    consentId: Option[String],
    status: Option[String],
    limit: Int,
    offset: Int,
    sortField: String,
    sortDirection: String
  ): ConnectionIO[List[ConsentRow]] = {
    val userCond = userId.map(v => fr"AND created_by_user_id = $v").getOrElse(fr"")
    val consumerCond = consumerId.map(v => fr"AND consumer_id = $v").getOrElse(fr"")
    val consentCond = consentId.map(v => fr"AND consent_id = $v").getOrElse(fr"")
    val statusCond = buildStatusCondition(status)
    val orderBy = buildOrderBy(sortField, sortDirection)

    val whereClause = fr"WHERE 1=1 " ++ userCond ++ consumerCond ++ consentCond ++ statusCond
    val dataQuery = selectColumns ++ fr" " ++ whereClause ++ fr" " ++ orderBy ++ fr" LIMIT $limit OFFSET $offset"

    dataQuery.query[ConsentRow].to[List]
  }

  /**
   * Insert consent items from the consent's views and entitlements.
   * Called at consent creation time after the JWT is set.
   */
  def insertConsentItems(consentReferenceId: String, consentJWT: code.api.util.ConsentJWT): Unit = {
    val viewInserts = consentJWT.views.filter(_.bank_id.nonEmpty).map { view =>
      val consentItemId = java.util.UUID.randomUUID().toString
      val itemType = "VIEW"
      val bankId = view.bank_id
      val accountId: Option[String] = Option(view.account_id).filter(_.nonEmpty)
      val viewId: Option[String] = Option(view.view_id).filter(_.nonEmpty)
      fr"""INSERT INTO consent_item (consent_item_id, consent_reference_id, item_type, bank_id, account_id, view_id)
           VALUES ($consentItemId, $consentReferenceId, $itemType, $bankId, $accountId, $viewId)""".update.run
    }
    val entitlementInserts = consentJWT.entitlements.filter(_.bank_id.nonEmpty).map { role =>
      val consentItemId = java.util.UUID.randomUUID().toString
      val itemType = "ENTITLEMENT"
      val bankId = role.bank_id
      val roleName: Option[String] = Option(role.role_name).filter(_.nonEmpty)
      fr"""INSERT INTO consent_item (consent_item_id, consent_reference_id, item_type, bank_id, role_name)
           VALUES ($consentItemId, $consentReferenceId, $itemType, $bankId, $roleName)""".update.run
    }
    val allInserts = viewInserts ++ entitlementInserts
    if (allInserts.nonEmpty) {
      val combined = allInserts.reduce((a, b) => a.flatMap(_ => b))
      DoobieUtil.runQuery(combined)
    }
  }

  /**
   * Get consents for a user filtered by bank_id, with pagination.
   * Uses the consent_item join table for efficient bank-scoped queries.
   */
  def getConsentsByUserAndBank(
    userId: String,
    bankId: String,
    status: Option[String],
    limit: Int,
    offset: Int,
    sortField: String,
    sortDirection: String
  ): List[ConsentRow] = {
    val query = buildGetConsentsByBankQuery(userId, bankId, status, limit, offset, sortField, sortDirection)
    DoobieUtil.runQuery(query)
  }

  private def buildGetConsentsByBankQuery(
    userId: String,
    bankId: String,
    status: Option[String],
    limit: Int,
    offset: Int,
    sortField: String,
    sortDirection: String
  ): ConnectionIO[List[ConsentRow]] = {
    val statusCond = buildStatusCondition(status)
    val orderBy = buildOrderBy(sortField, sortDirection)

    val dataQuery =
      fr"""SELECT DISTINCT v.consent_reference_id, v.consent_id, v.created_by_user_id, v.consumer_id,
           v.status, v.jwt, v.consent_request_id, v.api_standard, v.api_version,
           v.last_action_date, v.last_usage_date, v.created_date,
           v.note, v.frequency_per_day, v.uses_so_far_today_counter, v.jwt_payload, v.jwt_expires_at
           FROM v_consent v
           JOIN consent_item cb ON cb.consent_reference_id = v.consent_reference_id
           WHERE v.created_by_user_id = $userId
           AND cb.bank_id = $bankId""" ++ statusCond ++ fr" " ++ orderBy ++ fr" LIMIT $limit OFFSET $offset"

    dataQuery.query[ConsentRow].to[List]
  }
}
