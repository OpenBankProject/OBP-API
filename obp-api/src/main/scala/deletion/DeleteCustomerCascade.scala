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
import code.customer.MappedCustomer
import code.customer.internalMapping.MappedCustomerIdMapping
import code.customeraccountlinks.CustomerAccountLink
import code.customeraddress.MappedCustomerAddress
import code.customerattribute.MappedCustomerAttribute
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import code.taxresidence.MappedTaxResidence
import code.usercustomerlinks.MappedUserCustomerLink
import com.openbankproject.commons.model.CustomerId
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteCustomerCascade {

  def delete(customerId: CustomerId): Boolean = {
    val doneTasks =
        deleteCustomerAttributes(customerId) ::
          deleteTaxResidence(customerId) ::
          deleteKycStatus(customerId) ::
          deleteKycMedia(customerId) ::
          deleteKycCheck(customerId) ::
          deleteKycDocument(customerId) ::
          deleteCustomerAddress(customerId) ::
          deleteCustomerIdMapping(customerId) ::
          deleteAccountApplication(customerId) ::
          deleteCustomerUserCustomerLinks(customerId) ::
          deleteCustomer(customerId) ::
          deleteCustomerAccountLinks(customerId) ::
        Nil
    doneTasks.forall(_ == true)
  }
  
  def atomicDelete(customerId: CustomerId): Box[Boolean] = databaseAtomicTask {
    delete(customerId) match {
      case true =>
        Full(true)
      case false =>
        DB.rollback(DefaultConnectionIdentifier)
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }
  private def deleteCustomerAccountLinks(customerId: CustomerId): Boolean = {
    CustomerAccountLink.bulkDelete_!!(
      By(CustomerAccountLink.CustomerId, customerId.value)
    )
  }
  private def deleteCustomerAttributes(customerId: CustomerId): Boolean = {
    MappedCustomerAttribute.bulkDelete_!!(By(MappedCustomerAttribute.mCustomerId, customerId.value))
  }

  private def deleteCustomer(customerId: CustomerId): Boolean = {
    MappedCustomer.bulkDelete_!!(
      By(MappedCustomer.mCustomerId, customerId.value)
    )
  }
  private def deleteCustomerUserCustomerLinks(customerId: CustomerId): Boolean = {
    MappedUserCustomerLink.bulkDelete_!!(
      By(MappedUserCustomerLink.mCustomerId, customerId.value)
    )
  }
  private def deleteTaxResidence(customerId: CustomerId): Boolean = {
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId.value)).forall(c =>
      MappedTaxResidence.bulkDelete_!!(
        By(MappedTaxResidence.mCustomerId, c.id.get)
      ))
  }
  private def deleteKycStatus(customerId: CustomerId): Boolean = {
    MappedKycStatus.bulkDelete_!!(
      By(MappedKycStatus.mCustomerId, customerId.value)
    )
  }
  private def deleteKycMedia(customerId: CustomerId): Boolean = {
    MappedKycMedia.bulkDelete_!!(
      By(MappedKycMedia.mCustomerId, customerId.value)
    )
  }
  private def deleteKycCheck(customerId: CustomerId): Boolean = {
    MappedKycCheck.bulkDelete_!!(
      By(MappedKycCheck.mCustomerId, customerId.value)
    )
  }
  private def deleteKycDocument(customerId: CustomerId): Boolean = {
    MappedKycDocument.bulkDelete_!!(
      By(MappedKycDocument.mCustomerId, customerId.value)
    )
  }
  private def deleteCustomerAddress(customerId: CustomerId): Boolean = {
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId.value)).forall(c =>
      MappedCustomerAddress.bulkDelete_!!(
      By(MappedCustomerAddress.mCustomerId, c.id.get)
    ))
  }
  private def deleteAccountApplication(customerId: CustomerId): Boolean = {
    MappedAccountApplication.bulkDelete_!!(
      By(MappedAccountApplication.mCustomerId, customerId.value)
    )
  }
  private def deleteCustomerIdMapping(customerId: CustomerId): Boolean = {
    MappedCustomerIdMapping.bulkDelete_!!(
      By(MappedCustomerIdMapping.mCustomerId, customerId.value)
    )
  }

}
