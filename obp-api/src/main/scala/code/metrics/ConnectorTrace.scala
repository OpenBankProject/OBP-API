package code.metrics

import java.util.Date

import code.api.util.OBPQueryParam

/**
 * Connector traces. The Lift ConnectorTrace entity is gone: the table (connector_trace) is owned
 * by Liquibase and the queries live in DoobieConnectorTrace. This object keeps its name and shape so
 * the call sites in code.bankconnectors and Http4s600 did not have to change, and delegates.
 *
 * getAllConnectorTraces now answers with DoobieConnectorTrace.ConnectorTraceRow instead of the
 * entity - the only consumer is JSONFactory600.createConnectorTraceJsonV600, which reads fields.
 */
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
  ): Unit =
    DoobieConnectorTrace.saveConnectorTrace(correlationId, connectorName, functionName, bankId,
      outboundMessage, inboundMessage, date, duration, isSuccessful, userId, httpVerb, url)

  def getAllConnectorTraces(queryParams: List[OBPQueryParam]): List[DoobieConnectorTrace.ConnectorTraceRow] =
    DoobieConnectorTrace.getAllConnectorTraces(queryParams)
}
