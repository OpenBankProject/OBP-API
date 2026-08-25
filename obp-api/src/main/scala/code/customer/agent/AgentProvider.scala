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