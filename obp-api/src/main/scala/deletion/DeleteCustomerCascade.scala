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

package deletion

import code.accountapplication.MappedAccountApplication
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.api.util.DoobieUtil
import code.customer.MappedCustomer
import code.customeraccountlinks.DoobieCustomerAccountLinkProvider
import code.customeraddress.MappedCustomerAddress
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import com.openbankproject.commons.model.CustomerId
import deletion.DeletionUtil.databaseAtomicTask
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteCustomerCascade {

  def delete(customerId: CustomerId): Boolean = {
    // The tasks run in list order. Rows that point at the customer go first and the customer row goes last,
    // as the account cascade does with the account: some tasks read the customer row (its primary key, its
    // bank and number) to find their rows, and a failure of the last task rolls back all the others.
    val doneTasks =
        deleteCustomerAttributes(customerId) ::
          deleteTaxResidence(customerId) ::
          deleteKycStatus(customerId) ::
          deleteKycMedia(customerId) ::
          deleteKycCheck(customerId) ::
          deleteKycDocument(customerId) ::
          deleteCustomerAddress(customerId) ::
          deleteCustomerMessages(customerId) ::
          deleteCustomerDependants(customerId) ::
          deleteCustomerLinks(customerId) ::
          deleteCrmEvents(customerId) ::
          deleteCustomerIdMapping(customerId) ::
          deleteAccountApplication(customerId) ::
          deleteCustomerUserCustomerLinks(customerId) ::
          deleteCustomerAccountLinks(customerId) ::
          deleteCustomer(customerId) ::
        Nil
    doneTasks.forall(_ == true)
  }
  
  def atomicDelete(customerId: CustomerId): Box[Boolean] = databaseAtomicTask {
    delete(customerId) match {
      case true =>
        Full(true)
      case false =>
        // Throws, so databaseAtomicTask restores what delete has removed.
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }
  private def deleteCustomerMessages(customerId: CustomerId): Boolean = {
    MappedCustomer.findByCustomerId(customerId.value).forall { c =>
      DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomermessage WHERE customer = ${c.customerPrimaryKey}".update.run)
      true
    }
  }
  private def deleteCustomerDependants(customerId: CustomerId): Boolean = {
    MappedCustomer.findByCustomerId(customerId.value).forall { c =>
      DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomerdependant WHERE mcustomer = ${c.customerPrimaryKey}".update.run)
      true
    }
  }
  /** A link between two customers is held once, so the deleted customer can be on either side of it. */
  private def deleteCustomerLinks(customerId: CustomerId): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM customerlink WHERE customerid = ${customerId.value}".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM customerlink WHERE othercustomerid = ${customerId.value}".update.run)
    true
  }
  private def deleteCrmEvents(customerId: CustomerId): Boolean = {
    MappedCustomer.findByCustomerId(customerId.value).forall { c =>
      val number = Option(c.number).getOrElse("")
      number.isEmpty || {
        DoobieUtil.runUpdate(
          sql"DELETE FROM mappedcrmevent WHERE mbankid = ${c.bankId} AND mcustomernumber = $number".update.run)
        true
      }
    }
  }
  private def deleteCustomerAccountLinks(customerId: CustomerId): Boolean = {
    DoobieCustomerAccountLinkProvider.deleteByCustomerIdSync(customerId.value)
  }
  private def deleteCustomerAttributes(customerId: CustomerId): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomerattribute WHERE mcustomerid = ${customerId.value}".update.run)
    true
  }

  private def deleteCustomer(customerId: CustomerId): Boolean = {
    MappedCustomer.deleteByCustomerId(customerId.value)
  }
  private def deleteCustomerUserCustomerLinks(customerId: CustomerId): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedusercustomerlink WHERE mcustomerid = ${customerId.value}".update.run)
    true
  }
  private def deleteTaxResidence(customerId: CustomerId): Boolean = {
    MappedCustomer.findByCustomerId(customerId.value).forall { c =>
      DoobieUtil.runUpdate(sql"DELETE FROM mappedtaxresidence WHERE mcustomerid = ${c.customerPrimaryKey}".update.run)
      true
    }
  }
  private def deleteKycStatus(customerId: CustomerId): Boolean = {
    MappedKycStatus.deleteByCustomerId(customerId.value)
  }
  private def deleteKycMedia(customerId: CustomerId): Boolean = {
    MappedKycMedia.deleteByCustomerId(customerId.value)
  }
  private def deleteKycCheck(customerId: CustomerId): Boolean = {
    MappedKycCheck.deleteByCustomerId(customerId.value)
  }
  private def deleteKycDocument(customerId: CustomerId): Boolean = {
    MappedKycDocument.deleteByCustomerId(customerId.value)
  }
  private def deleteCustomerAddress(customerId: CustomerId): Boolean = {
    MappedCustomer.findByCustomerId(customerId.value).forall(c =>
      MappedCustomerAddress.deleteByCustomerKey(c.customerPrimaryKey)
    )
  }
  private def deleteAccountApplication(customerId: CustomerId): Boolean = {
    MappedAccountApplication.deleteByCustomerId(customerId.value)
  }
  private def deleteCustomerIdMapping(customerId: CustomerId): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomeridmapping WHERE mcustomerid = ${customerId.value}".update.run)
    true
  }

}
