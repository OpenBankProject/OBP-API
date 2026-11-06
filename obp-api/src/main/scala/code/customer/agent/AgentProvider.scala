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

package code.customer.agent

import code.api.util.{CallContext, OBPQueryParam}
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object AgentX extends SimpleInjector {

  val agentProvider = new Inject(() => buildOne) {}

  def buildOne: AgentProvider = MappedAgentProvider

}

trait AgentProvider {
  def getAgentsAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]]

  def getAgentsFuture(bankId: BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]]

  def getAgentsByAgentPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Agent]]]

  def getAgentsByAgentLegalName(bankId: BankId, legalName: String): Future[Box[List[Agent]]]

  def getAgentByAgentId(agentId: String): Box[Agent]

  def getAgentByAgentIdFuture(agentId: String): Future[Box[Agent]]

  def getBankIdByAgentId(agentId: String): Box[String]

  def getAgentByAgentNumber(bankId: BankId, agentNumber: String): Box[Agent]

  def getAgentByAgentNumberFuture(bankId: BankId, agentNumber: String): Future[Box[Agent]]

  def checkAgentNumberAvailable(bankId: BankId, agentNumber: String): Boolean

  def createAgent(
    bankId: String,
    legalName : String,
    mobileNumber : String,
    agentNumber : String,
    callContext: Option[CallContext]
  ): Future[Box[Agent]]

  def updateAgentStatus(
    agentId: String,
    isPendingAgent: Boolean,
    isConfirmedAgent: Boolean,
    callContext: Option[CallContext]
  ): Future[Box[Agent]]
  
}