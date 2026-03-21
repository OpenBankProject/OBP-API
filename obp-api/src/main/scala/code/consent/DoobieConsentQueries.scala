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
 * The jwt_payload field is NOT included in the view because it requires
 * JWT decoding in Scala — it is computed from the jwt column after the query.
 */
object DoobieConsentQueries {

  /**
   * Row type matching the v_consent view columns.
   * Fields align with ConsentInfoJsonV510.
   */
  case class ConsentRow(
    consentReferenceId: Long,
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
    usesSoFarTodayCounter: Option[Int]
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
  ): (List[ConsentRow], Long) = {
    val query = buildGetConsentsQuery(userId, status, limit, offset, sortField, sortDirection)
    DoobieUtil.runQuery(query)
  }

  def getConsentsByUserFuture(
    userId: String,
    status: Option[String],
    limit: Int,
    offset: Int,
    sortField: String,
    sortDirection: String
  )(implicit ec: ExecutionContext): Future[(List[ConsentRow], Long)] = {
    Future {
      getConsentsByUser(userId, status, limit, offset, sortField, sortDirection)
    }
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
           note, frequency_per_day, uses_so_far_today_counter
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
  ): (List[ConsentRow], Long) = {
    val query = buildFilteredQuery(userId, consumerId, consentId, status, limit, offset, sortField, sortDirection)
    DoobieUtil.runQuery(query)
  }

  private val selectColumns =
    fr"""SELECT consent_reference_id, consent_id, created_by_user_id, consumer_id,
         status, jwt, consent_request_id, api_standard, api_version,
         last_action_date, last_usage_date, created_date,
         note, frequency_per_day, uses_so_far_today_counter
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
  ): ConnectionIO[(List[ConsentRow], Long)] = {
    val statusCond = buildStatusCondition(status)
    val orderBy = buildOrderBy(sortField, sortDirection)

    val whereClause = fr"WHERE created_by_user_id = $userId " ++ statusCond
    val countQuery = fr"SELECT count(*) FROM v_consent " ++ whereClause
    val dataQuery = selectColumns ++ fr" " ++ whereClause ++ fr" " ++ orderBy ++ fr" LIMIT $limit OFFSET $offset"

    for {
      total <- countQuery.query[Long].unique
      rows  <- dataQuery.query[ConsentRow].to[List]
    } yield (rows, total)
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
  ): ConnectionIO[(List[ConsentRow], Long)] = {
    val userCond = userId.map(v => fr"AND created_by_user_id = $v").getOrElse(fr"")
    val consumerCond = consumerId.map(v => fr"AND consumer_id = $v").getOrElse(fr"")
    val consentCond = consentId.map(v => fr"AND consent_id = $v").getOrElse(fr"")
    val statusCond = buildStatusCondition(status)
    val orderBy = buildOrderBy(sortField, sortDirection)

    val whereClause = fr"WHERE 1=1 " ++ userCond ++ consumerCond ++ consentCond ++ statusCond
    val countQuery = fr"SELECT count(*) FROM v_consent " ++ whereClause
    val dataQuery = selectColumns ++ fr" " ++ whereClause ++ fr" " ++ orderBy ++ fr" LIMIT $limit OFFSET $offset"

    for {
      total <- countQuery.query[Long].unique
      rows  <- dataQuery.query[ConsentRow].to[List]
    } yield (rows, total)
  }
}
