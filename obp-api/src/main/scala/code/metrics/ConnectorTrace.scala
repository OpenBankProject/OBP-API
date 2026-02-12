package code.metrics

import java.util.Date

import code.api.util._
import net.liftweb.mapper._

class ConnectorTrace extends LongKeyedMapper[ConnectorTrace] with IdPK {
  override def getSingleton = ConnectorTrace

  object correlationId extends MappedString(this, 256)
  object connectorName extends MappedString(this, 64)
  object functionName extends MappedString(this, 128)
  object bankId extends MappedString(this, 256)
  object outboundMessage extends MappedText(this)
  object inboundMessage extends MappedText(this)
  object date extends MappedDateTime(this)
  object duration extends MappedLong(this)
  object isSuccessful extends MappedBoolean(this)
  object userId extends MappedString(this, 256)
  object httpVerb extends MappedString(this, 16)
  object url extends MappedString(this, 2000)
}

object ConnectorTrace extends ConnectorTrace with LongKeyedMetaMapper[ConnectorTrace] {
  override def dbTableName = "connector_trace"
  override def dbIndexes = Index(correlationId) :: Index(connectorName) :: Index(functionName) ::
    Index(date) :: Index(userId) :: Index(bankId) :: super.dbIndexes
}

object ConnectorTraceProvider {

  def saveConnectorTrace(
    correlationId: String,
    connectorName: String,
    functionName: String,
    bankId: String,
    outboundMessage: String,
    inboundMessage: String,
    date: Date,
    duration: Long,
    isSuccessful: Boolean,
    userId: String,
    httpVerb: String,
    url: String
  ): Unit = {
    ConnectorTrace.create
      .correlationId(correlationId)
      .connectorName(connectorName)
      .functionName(functionName)
      .bankId(bankId)
      .outboundMessage(outboundMessage)
      .inboundMessage(inboundMessage)
      .date(date)
      .duration(duration)
      .isSuccessful(isSuccessful)
      .userId(userId)
      .httpVerb(httpVerb)
      .url(url)
      .save
  }

  def getAllConnectorTraces(queryParams: List[OBPQueryParam]): List[ConnectorTrace] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[ConnectorTrace](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[ConnectorTrace](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(ConnectorTrace.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(ConnectorTrace.date, date) }.headOption
    val correlationId = queryParams.collect { case OBPCorrelationId(value) => By(ConnectorTrace.correlationId, value) }.headOption
    val functionName = queryParams.collect { case OBPFunctionName(value) => By(ConnectorTrace.functionName, value) }.headOption
    val connectorName = queryParams.collect { case OBPConnectorName(value) => By(ConnectorTrace.connectorName, value) }.headOption
    val userId = queryParams.collect { case OBPUserId(value) => By(ConnectorTrace.userId, value) }.headOption
    val bankId = queryParams.collect { case OBPBankId(value) => By(ConnectorTrace.bankId, value) }.headOption
    val ordering = queryParams.collect {
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(ConnectorTrace.date, Ascending)
          case OBPDescending => OrderBy(ConnectorTrace.date, Descending)
        }
    }
    val optionalParams: Seq[QueryParam[ConnectorTrace]] = Seq(
      limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering,
      correlationId.toSeq, functionName.toSeq, connectorName.toSeq,
      userId.toSeq, bankId.toSeq
    ).flatten

    ConnectorTrace.findAll(optionalParams: _*)
  }
}
