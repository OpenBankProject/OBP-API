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

package code.customeraccountlinks

import code.api.util.ErrorMessages
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.model.{CustomerAccountLinkTrait,AgentAccountLinkTrait}

object MappedCustomerAccountLinkProvider extends CustomerAccountLinkProvider {
  override def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] = {
    tryo {
      CustomerAccountLink.create
      .CustomerId(customerId)
      .BankId(bankId)
      .AccountId(accountId)
      .RelationshipType(relationshipType)
      .saveMe()
    }
  }
  override def getOrCreateCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] = {
    CustomerAccountLink.find(
      By(CustomerAccountLink.CustomerId, customerId),
      By(CustomerAccountLink.BankId, bankId),
      By(CustomerAccountLink.AccountId, accountId)
    ) match {
      case Empty =>
        val createCustomerAccountLink = CustomerAccountLink.create
          .CustomerId(customerId)
          .BankId(bankId)
          .AccountId(accountId)
          .RelationshipType(relationshipType)
          .saveMe()
        Some(createCustomerAccountLink)
      case everythingElse => everythingElse
    }
  }

  override def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait] = {
    CustomerAccountLink.find(
      By(CustomerAccountLink.CustomerId, customerId))
  }


  override def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] = {
    tryo {
      CustomerAccountLink.findAll(
        By(CustomerAccountLink.BankId, bankId),
        By(CustomerAccountLink.AccountId, accountId)
      )
    }
  }

  override def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]] = {
    tryo {
      CustomerAccountLink.findAll(
        By(CustomerAccountLink.CustomerId, customerId))
    }
  }


  override def getCustomerAccountLinksByAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] = {
    tryo {
      CustomerAccountLink.findAll(
        By(CustomerAccountLink.BankId, bankId),
        By(CustomerAccountLink.AccountId, accountId))
    }
  }

  override def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait] = {
    CustomerAccountLink.find(
      By(CustomerAccountLink.CustomerAccountLinkId, customerAccountLinkId)
    )
  }

  override def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait] = {
    CustomerAccountLink.find(By(CustomerAccountLink.CustomerAccountLinkId, customerAccountLinkId)) match {
      case Full(t) => Full(t.RelationshipType(relationshipType).saveMe())
      case Empty => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
      case Failure(msg, exception, chain) => Failure(msg, exception, chain)
    }
  }

  override def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]] = {
    tryo {CustomerAccountLink.findAll()}
  }

  override def bulkDeleteCustomerAccountLinks(): Boolean = {
    CustomerAccountLink.bulkDelete_!!()
  }

  override def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]] = {
    Future {
      CustomerAccountLink.find(By(CustomerAccountLink.CustomerAccountLinkId, customerAccountLinkId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
        case Failure(msg, exception, chain) => Failure(msg, exception, chain)
      }
    }
  }
}

//in OBP, customer and agent share the same customer model. the CustomerAccountLink and AgentAccountLink also share the same model
class CustomerAccountLink extends CustomerAccountLinkTrait with AgentAccountLinkTrait with LongKeyedMapper[CustomerAccountLink] with IdPK with CreatedUpdated {

  def getSingleton = CustomerAccountLink

  object CustomerAccountLinkId extends MappedUUID(this)
  object CustomerId extends UUIDString(this)
  object BankId extends MappedString(this, 255)
  object AccountId extends UUIDString(this)
  object RelationshipType extends MappedString(this, 255)

  override def customerAccountLinkId: String = CustomerAccountLinkId.get
  override def customerId: String = CustomerId.get // id.toString
  override def bankId: String = BankId.get // id.toString
  override def accountId: String = AccountId.get
  override def relationshipType: String = RelationshipType.get
  
  override def agentId: String = CustomerId.get
  override def agentAccountLinkId: String = CustomerAccountLinkId.get
  
}

object CustomerAccountLink extends CustomerAccountLink with LongKeyedMetaMapper[CustomerAccountLink] {
  override def dbIndexes = UniqueIndex(CustomerAccountLinkId) :: UniqueIndex(AccountId, CustomerId) :: super.dbIndexes

}
