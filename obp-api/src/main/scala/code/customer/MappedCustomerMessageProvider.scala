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

package code.customer

import java.util.Date
import code.model.dataAccess.ResourceUser
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.{BankId, Customer, CustomerMessage, User}
import net.liftweb.mapper._

object MappedCustomerMessageProvider extends CustomerMessageProvider {

  override def getMessages(user: User, bankId : BankId): List[CustomerMessage] = {
    MappedCustomerMessage.findAll(
      By(MappedCustomerMessage.user, user.userPrimaryKey.value),
      By(MappedCustomerMessage.bank, bankId.value),
      OrderBy(MappedCustomerMessage.updatedAt, Descending))
  }


  override def addMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String) = {
    MappedCustomerMessage.create
      .mFromDepartment(fromDepartment)
      .mFromPerson(fromPerson)
      .mMessage(message)
      .user(user.userPrimaryKey.value)
      .bank(bankId.value).saveMe()
  }

  override def createCustomerMessage(customer: Customer, bankId: BankId, transport: String, message: String, fromDepartment: String, fromPerson: String) = {
    val mappedCustomer = MappedCustomer.find(By(MappedCustomer.mCustomerId, customer.customerId)).head
    MappedCustomerMessage.create
      .mFromDepartment(fromDepartment)
      .mFromPerson(fromPerson)
      .mTransport(transport)
      .mMessage(message)
      .customer(mappedCustomer)
      .bank(bankId.value).saveMe()
  }
  
  override def getCustomerMessages(customer : Customer, bankId : BankId) : List[CustomerMessage] = {
    val mappedCustomer = MappedCustomer.find(By(MappedCustomer.mCustomerId, customer.customerId)).head
    MappedCustomerMessage.findAll(
      By(MappedCustomerMessage.customer, mappedCustomer.primaryKeyField.get),
      By(MappedCustomerMessage.bank, bankId.value),
      OrderBy(MappedCustomerMessage.updatedAt, Descending))
  }
  
}

class MappedCustomerMessage extends CustomerMessage
  with LongKeyedMapper[MappedCustomerMessage] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerMessage

  @deprecated("We need user customer not user as the foreign key","15-03-2022")
  object user extends MappedLongForeignKey(this, ResourceUser)
  object customer extends MappedLongForeignKey(this, MappedCustomer)
  object bank extends UUIDString(this)

  object mFromPerson extends MappedString(this, 64)
  object mFromDepartment extends MappedString(this, 64)
  object mMessage extends MappedString(this, 1024)
  object mMessageId extends MappedUUID(this)
  object mTransport extends MappedString(this, 64)


  override def messageId: String = mMessageId.get
  override def date: Date = createdAt.get
  override def fromPerson: String = mFromPerson.get
  override def fromDepartment: String = mFromDepartment.get
  override def message: String = mMessage.get
  override def transport: Option[String] = if (mTransport.get == null || mTransport.get.isEmpty) None else Some(mTransport.get)
}

object MappedCustomerMessage extends MappedCustomerMessage with LongKeyedMetaMapper[MappedCustomerMessage] {
  override def dbIndexes = UniqueIndex(mMessageId) :: super.dbIndexes
}