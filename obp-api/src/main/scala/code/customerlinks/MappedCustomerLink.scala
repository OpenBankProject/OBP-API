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

package code.customerlinks

import java.util.Date

import code.api.util.ErrorMessages
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedCustomerLinkProvider extends CustomerLinkProvider {
  override def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLinkTrait] = {
    tryo {
      CustomerLink.create
        .BankId(bankId)
        .CustomerId(customerId)
        .OtherBankId(otherBankId)
        .OtherCustomerId(otherCustomerId)
        .RelationshipTo(relationshipTo)
        .saveMe()
    }
  }

  override def getCustomerLinkById(customerLinkId: String): Box[CustomerLinkTrait] = {
    CustomerLink.find(
      By(CustomerLink.CustomerLinkId, customerLinkId)
    )
  }

  override def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLinkTrait]] = {
    tryo {
      CustomerLink.findAll(
        By(CustomerLink.BankId, bankId))
    }
  }

  override def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLinkTrait]] = {
    tryo {
      CustomerLink.findAll(
        By(CustomerLink.CustomerId, customerId))
    }
  }

  override def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLinkTrait] = {
    CustomerLink.find(By(CustomerLink.CustomerLinkId, customerLinkId)) match {
      case Full(t) => Full(t.RelationshipTo(relationshipTo).saveMe())
      case Empty => Empty ?~! ErrorMessages.CustomerLinkNotFound
      case Failure(msg, exception, chain) => Failure(msg, exception, chain)
    }
  }

  override def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]] = {
    Future {
      CustomerLink.find(By(CustomerLink.CustomerLinkId, customerLinkId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.CustomerLinkNotFound
        case Failure(msg, exception, chain) => Failure(msg, exception, chain)
      }
    }
  }

  override def bulkDeleteCustomerLinks(): Boolean = {
    CustomerLink.bulkDelete_!!()
  }
}

class CustomerLink extends CustomerLinkTrait with LongKeyedMapper[CustomerLink] with IdPK with CreatedUpdated {

  def getSingleton = CustomerLink

  object CustomerLinkId extends MappedUUID(this)
  object BankId extends MappedString(this, 255)
  object CustomerId extends UUIDString(this)
  object OtherBankId extends MappedString(this, 255)
  object OtherCustomerId extends UUIDString(this)
  object RelationshipTo extends MappedString(this, 255)

  override def customerLinkId: String = CustomerLinkId.get
  override def bankId: String = BankId.get
  override def customerId: String = CustomerId.get
  override def otherBankId: String = OtherBankId.get
  override def otherCustomerId: String = OtherCustomerId.get
  override def relationshipTo: String = RelationshipTo.get
  override def dateInserted: Date = createdAt.get
  override def dateUpdated: Date = updatedAt.get
}

object CustomerLink extends CustomerLink with LongKeyedMetaMapper[CustomerLink] {
  override def dbTableName = "CustomerLink"
  override def dbIndexes = UniqueIndex(CustomerLinkId) :: Index(CustomerId) :: Index(OtherCustomerId) :: super.dbIndexes
}
