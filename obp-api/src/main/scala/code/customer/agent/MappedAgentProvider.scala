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
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object MappedAgentProvider extends AgentProvider with MdcLoggable {

  override def getAgentsAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]] = Future {
    Full(MappedCustomer.findAll(bankId = None, customerTypes = None,
      MappedCustomerProvider.getOptionalParams(queryParams)))
  }

  override def getAgentsFuture(bankId: BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]] = Future {
    Full(MappedCustomer.findAll(Some(bankId.value), customerTypes = None,
      MappedCustomerProvider.getOptionalParams(queryParams)))
  }
  

  override def getAgentsByAgentPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Agent]]] = Future {
    Full(MappedCustomer.findAllByBankAndMobileNumberLike(bankId.value, phoneNumber))
  }

  override def getAgentsByAgentLegalName(bankId: BankId, legalName: String): Future[Box[List[Agent]]] = Future {
    Full(MappedCustomer.findAllByBankAndLegalNameLike(bankId.value, legalName))
  }


  override def checkAgentNumberAvailable(bankId: BankId, agentNumber: String): Boolean = {
    val customers = MappedCustomer.findAllByBankAndNumber(bankId.value, agentNumber)

    val available: Boolean = customers.size match {
      case 0 => true
      case _ => false
    }

    available
  }

  override def getAgentByAgentId(agentId: String): Box[Agent] =
    MappedCustomer.findByCustomerId(agentId)

  override def getBankIdByAgentId(agentId: String): Box[String] = {
    for (c <- MappedCustomer.findByCustomerId(agentId)) yield {
      c.bankId
    }
  }

  override def getAgentByAgentNumber(bankId: BankId, agentNumber: String): Box[Agent] =
    MappedCustomer.findByBankAndNumber(bankId.value, agentNumber)

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
      // The fields an agent does not carry keep the same defaults Mapper's untouched fields had:
      // empty strings, no dates, zero dependants, INDIVIDUAL as the customer type.
      MappedCustomer.insert(
        bankIdValue = bankId,
        email = "", faceImageTime = null, faceImageUrl = "",
        legalName = legalName,
        mobileNumber = mobileNumber,
        number = agentNumber,
        dateOfBirth = null, relationshipStatus = "", dependents = 0,
        highestEducationAttained = "", employmentStatus = "", kycStatus = false,
        lastOkDate = null, creditRating = "", creditSource = "", creditLimitCurrency = "",
        creditLimitAmount = "", title = "", branchId = "", nameSuffix = "",
        customerType = "INDIVIDUAL", parentCustomerId = "",
        isPendingAgent = true, //default value
        isConfirmedAgent = false) // default value

    }

  }

  override def updateAgentStatus(
    agentId: String,
    isPendingAgent: Boolean,
    isConfirmedAgent: Boolean,
    callContext: Option[CallContext]
  ): Future[Box[Agent]] = Future {
    MappedCustomer.findByCustomerId(agentId) map { c =>
      MappedCustomer.setAgentStatus(c.customerId, isPendingAgent, isConfirmedAgent)
    }
  }

  override def getAgentByAgentIdFuture(agentId: String): Future[Box[Agent]] = Future {
    getAgentByAgentId(agentId: String)
  }
}