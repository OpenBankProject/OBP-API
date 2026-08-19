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
