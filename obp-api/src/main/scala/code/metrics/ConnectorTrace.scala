package code.metrics

import java.util.Date

import code.api.util._
import net.liftweb.mapper._

class MappedConnectorTrace extends LongKeyedMapper[MappedConnectorTrace] with IdPK {
  override def getSingleton = MappedConnectorTrace

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

object MappedConnectorTrace extends MappedConnectorTrace with LongKeyedMetaMapper[MappedConnectorTrace] {
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
    MappedConnectorTrace.create
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

  def getAllConnectorTraces(queryParams: List[OBPQueryParam]): List[MappedConnectorTrace] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedConnectorTrace](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedConnectorTrace](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedConnectorTrace.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedConnectorTrace.date, date) }.headOption
    val correlationId = queryParams.collect { case OBPCorrelationId(value) => By(MappedConnectorTrace.correlationId, value) }.headOption
    val functionName = queryParams.collect { case OBPFunctionName(value) => By(MappedConnectorTrace.functionName, value) }.headOption
    val connectorName = queryParams.collect { case OBPConnectorName(value) => By(MappedConnectorTrace.connectorName, value) }.headOption
    val userId = queryParams.collect { case OBPUserId(value) => By(MappedConnectorTrace.userId, value) }.headOption
    val bankId = queryParams.collect { case OBPBankId(value) => By(MappedConnectorTrace.bankId, value) }.headOption
    val ordering = queryParams.collect {
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedConnectorTrace.date, Ascending)
          case OBPDescending => OrderBy(MappedConnectorTrace.date, Descending)
        }
    }
    val optionalParams: Seq[QueryParam[MappedConnectorTrace]] = Seq(
      limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering,
      correlationId.toSeq, functionName.toSeq, connectorName.toSeq,
      userId.toSeq, bankId.toSeq
    ).flatten

    MappedConnectorTrace.findAll(optionalParams: _*)
  }
}
