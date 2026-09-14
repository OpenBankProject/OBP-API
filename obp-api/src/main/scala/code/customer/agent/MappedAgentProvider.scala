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

import code.api.util._
import code.customer.{MappedCustomer, MappedCustomerProvider}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object MappedAgentProvider extends AgentProvider with MdcLoggable {

  override def getAgentsAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]] = Future {
    val mapperParams = MappedCustomerProvider.getOptionalParams(queryParams)
    Full(MappedCustomer.findAll(mapperParams: _*))
  }

  override def getAgentsFuture(bankId: BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]] = Future {
    val mapperParams = Seq(By(MappedCustomer.mBank, bankId.value)) ++ MappedCustomerProvider.getOptionalParams(queryParams)
    Full(MappedCustomer.findAll(mapperParams: _*))
  }
  

  override def getAgentsByAgentPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Agent]]] = Future {
    val result = MappedCustomer.findAll(
      By(MappedCustomer.mBank, bankId.value),
      Like(MappedCustomer.mMobileNumber, phoneNumber)
    )
    Full(result)
  }

  override def getAgentsByAgentLegalName(bankId: BankId, legalName: String): Future[Box[List[Agent]]] = Future {
    val result = MappedCustomer.findAll(
      By(MappedCustomer.mBank, bankId.value),
      Like(MappedCustomer.mLegalName, legalName)
    )
    Full(result)
  }


  override def checkAgentNumberAvailable(bankId: BankId, agentNumber: String): Boolean = {
    val customers = MappedCustomer.findAll(
      By(MappedCustomer.mBank, bankId.value),
      By(MappedCustomer.mNumber, agentNumber)
    )

    val available: Boolean = customers.size match {
      case 0 => true
      case _ => false
    }

    available
  }

  override def getAgentByAgentId(agentId: String): Box[Agent] = {
    MappedCustomer.find(
      By(MappedCustomer.mCustomerId, agentId)
    )
  }

  override def getBankIdByAgentId(agentId: String): Box[String] = {
    val customer: Box[MappedCustomer] = MappedCustomer.find(
      By(MappedCustomer.mCustomerId, agentId)
    )
    for (c <- customer) yield {
      c.mBank.get
    }
  }

  override def getAgentByAgentNumber(bankId: BankId, agentNumber: String): Box[Agent] = {
    MappedCustomer.find(
      By(MappedCustomer.mNumber, agentNumber),
      By(MappedCustomer.mBank, bankId.value)
    )
  }

  override def getAgentByAgentNumberFuture(bankId: BankId, agentNumber: String): Future[Box[Agent]] = {
    Future(getAgentByAgentNumber(bankId: BankId, agentNumber: String))
  }


  override def createAgent(
    bankId: String,
    legalName: String,
    mobileNumber: String,
    agentNumber: String,
    callContext: Option[CallContext]
  ): Future[Box[Agent]] = Future {
    tryo {
      MappedCustomer
        .create
        .mBank(bankId)
        .mLegalName(legalName)
        .mMobileNumber(mobileNumber)
        .mNumber(agentNumber)
        .mIsPendingAgent(true) //default value
        .mIsConfirmedAgent(false) // default value
        .saveMe()

    }

  }

  override def updateAgentStatus(
    agentId: String,
    isPendingAgent: Boolean,
    isConfirmedAgent: Boolean,
    callContext: Option[CallContext]
  ): Future[Box[Agent]] = Future {
    MappedCustomer.find(
      By(MappedCustomer.mCustomerId, agentId)
    ) map {
      c =>
        c.mIsPendingAgent(isPendingAgent)
        c.mIsConfirmedAgent(isConfirmedAgent)
        c.saveMe()
    }
  }

  override def getAgentByAgentIdFuture(agentId: String): Future[Box[Agent]] = Future {
    getAgentByAgentId(agentId: String)
  }
}