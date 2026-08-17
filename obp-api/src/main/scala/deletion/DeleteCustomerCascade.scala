package deletion

import code.accountapplication.MappedAccountApplication
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.api.util.DoobieUtil
import code.customer.MappedCustomer
import code.customeraccountlinks.CustomerAccountLink
import code.customeraddress.MappedCustomerAddress
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import code.taxresidence.MappedTaxResidence
import code.usercustomerlinks.MappedUserCustomerLink
import com.openbankproject.commons.model.CustomerId
import deletion.DeletionUtil.databaseAtomicTask
import doobie.implicits._
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
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomerattribute WHERE mcustomerid = ${customerId.value}".update.run)
    true
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
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomeridmapping WHERE mcustomerid = ${customerId.value}".update.run)
    true
  }

}
