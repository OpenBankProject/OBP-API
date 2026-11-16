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
import code.CustomerDependants.MappedCustomerDependant
import code.customer.{MappedCustomer, MappedCustomerMessage}
import code.customer.internalMapping.MappedCustomerIdMapping
import code.customeraccountlinks.CustomerAccountLink
import code.customeraddress.MappedCustomerAddress
import code.crm.MappedCrmEvent
import code.customerlinks.CustomerLink
import code.customerattribute.MappedCustomerAttribute
import code.directdebit.DirectDebit
import code.mandate.{Mandate, MandateProvision, SignatoryPanel}
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import code.socialmedia.MappedSocialMedia
import code.standingorders.StandingOrder
import code.taxresidence.MappedTaxResidence
import code.usercustomerlinks.MappedUserCustomerLink
import com.openbankproject.commons.model.CustomerId
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{By, ByList, NotBy}

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
          deleteSocialMedia(customerId) ::
          deleteCrmEvents(customerId) ::
          deleteStandingOrders(customerId) ::
          deleteDirectDebits(customerId) ::
          deleteMandates(customerId) ::
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
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId.value)).forall(c =>
      MappedCustomerMessage.bulkDelete_!!(
        By(MappedCustomerMessage.customer, c.id.get)
      ))
  }
  private def deleteCustomerDependants(customerId: CustomerId): Boolean = {
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId.value)).forall(c =>
      MappedCustomerDependant.bulkDelete_!!(
        By(MappedCustomerDependant.mCustomer, c.id.get)
      ))
  }
  /** A link between two customers is held once, so the deleted customer can be on either side of it. */
  private def deleteCustomerLinks(customerId: CustomerId): Boolean = {
    CustomerLink.bulkDelete_!!(By(CustomerLink.CustomerId, customerId.value)) &&
      CustomerLink.bulkDelete_!!(By(CustomerLink.OtherCustomerId, customerId.value))
  }
  /**
   * Social media rows carry the customer number and no bank, and a customer number is only unique within a bank.
   * They are deleted only when no customer of another bank has the same number, so one customer's cascade
   * never removes another customer's handles.
   */
  private def deleteSocialMedia(customerId: CustomerId): Boolean = {
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId.value)).forall { c =>
      val number = c.mNumber.get
      val numberUsedByAnotherCustomer =
        MappedCustomer.count(By(MappedCustomer.mNumber, number), NotBy(MappedCustomer.mCustomerId, customerId.value)) > 0
      number.isEmpty || numberUsedByAnotherCustomer ||
        MappedSocialMedia.bulkDelete_!!(By(MappedSocialMedia.mCustomerNumber, number))
    }
  }
  private def deleteCrmEvents(customerId: CustomerId): Boolean = {
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId.value)).forall { c =>
      val number = c.mNumber.get
      number.isEmpty || MappedCrmEvent.bulkDelete_!!(
        By(MappedCrmEvent.mBankId, c.mBank.get),
        By(MappedCrmEvent.mCustomerNumber, number)
      )
    }
  }
  private def deleteStandingOrders(customerId: CustomerId): Boolean = {
    StandingOrder.bulkDelete_!!(By(StandingOrder.CustomerId, customerId.value))
  }
  private def deleteDirectDebits(customerId: CustomerId): Boolean = {
    DirectDebit.bulkDelete_!!(By(DirectDebit.CustomerId, customerId.value))
  }
  /** A mandate's provisions and signatory panels are found through the mandate id, so they go before the mandate. */
  private def deleteMandates(customerId: CustomerId): Boolean = {
    val mandateIds = Mandate.findAll(By(Mandate.CustomerId, customerId.value)).map(_.MandateId.get)
    mandateIds.isEmpty || (
      MandateProvision.bulkDelete_!!(ByList(MandateProvision.MandateId, mandateIds)) &&
        SignatoryPanel.bulkDelete_!!(ByList(SignatoryPanel.MandateId, mandateIds)) &&
        Mandate.bulkDelete_!!(By(Mandate.CustomerId, customerId.value))
      )
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
