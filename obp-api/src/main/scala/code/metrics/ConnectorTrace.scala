/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
