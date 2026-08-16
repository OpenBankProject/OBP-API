package code.metrics

import java.sql.Timestamp
import java.util.Date

import code.api.util._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._  // Meta instances for java.sql.Timestamp

/**
 * Doobie implementation of the connector-trace store, replacing the Lift ConnectorTrace entity.
 *
 * Written rather than ported - the reference branch never migrated this table.
 *
 * The table is "connector_trace", not "connectortrace": the entity overrode dbTableName. Column
 * names follow the field names, with date_c for `date` (Lift escapes the SQL reserved word).
 *
 * getAll takes nine independent filters plus ordering and paging. Each is optional and they
 * combine with AND, matching what the Lift query built out of QueryParams.
 * ConnectorTraceProviderTest pins every one of them.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on an autoCommit=false pool, so an insert would be rolled back on return.
 */
object DoobieConnectorTrace {

  case class ConnectorTraceRow(
    id: Long,
    correlationId: String,
    connectorName: String,
    functionName: String,
    bankId: String,
    outboundMessage: String,
    inboundMessage: String,
    date: Option[Timestamp],
    duration: Long,
    isSuccessful: Boolean,
    userId: String,
    httpVerb: String,
    url: String
  )

  private val selectCols: Fragment =
    fr"""SELECT id, correlationid, connectorname, functionname, bankid, outboundmessage,
                inboundmessage, date_c, duration, issuccessful, userid, httpverb, url
         FROM connector_trace"""

  def saveConnectorTrace(correlationId: String, connectorName: String, functionName: String,
                         bankId: String, outboundMessage: String, inboundMessage: String,
                         date: Date, duration: Long, isSuccessful: Boolean, userId: String,
                         httpVerb: String, url: String): Unit = {
    val ts = new Timestamp(date.getTime)
    DoobieUtil.runUpdate(
      sql"""INSERT INTO connector_trace
              (correlationid, connectorname, functionname, bankid, outboundmessage, inboundmessage,
               date_c, duration, issuccessful, userid, httpverb, url)
            VALUES ($correlationId, $connectorName, $functionName, $bankId, $outboundMessage,
                    $inboundMessage, $ts, $duration, $isSuccessful, $userId, $httpVerb, $url)"""
        .update.run)
    ()
  }

  def getAllConnectorTraces(queryParams: List[OBPQueryParam]): List[ConnectorTraceRow] = {
    val conditions: List[Fragment] = List(
      queryParams.collectFirst { case OBPFromDate(d)        => fr"date_c >= ${new Timestamp(d.getTime)}" },
      queryParams.collectFirst { case OBPToDate(d)          => fr"date_c <= ${new Timestamp(d.getTime)}" },
      queryParams.collectFirst { case OBPCorrelationId(v)   => fr"correlationid = $v" },
      queryParams.collectFirst { case OBPFunctionName(v)    => fr"functionname = $v" },
      queryParams.collectFirst { case OBPConnectorName(v)   => fr"connectorname = $v" },
      queryParams.collectFirst { case OBPUserId(v)          => fr"userid = $v" },
      queryParams.collectFirst { case OBPBankId(v)          => fr"bankid = $v" }
    ).flatten

    val whereFr =
      if (conditions.isEmpty) Fragment.empty
      else fr"WHERE" ++ conditions.reduceLeft((a, b) => a ++ fr"AND" ++ b)

    val orderFr = queryParams.collectFirst {
      case OBPOrdering(_, OBPAscending)  => fr"ORDER BY date_c ASC"
      case OBPOrdering(_, OBPDescending) => fr"ORDER BY date_c DESC"
    }.getOrElse(Fragment.empty)

    val limitFr  = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
    val offsetFr = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)

    DoobieUtil.runQuery(
      (selectCols ++ whereFr ++ orderFr ++ limitFr ++ offsetFr).query[ConnectorTraceRow].to[List])
  }
}
