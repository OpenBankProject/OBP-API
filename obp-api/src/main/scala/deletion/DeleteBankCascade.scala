package deletion

import code.accountattribute.DoobieAccountAttributeProvider
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.customer.CustomerX
import code.customeraccountlinks.DoobieCustomerAccountLinkProvider
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import com.openbankproject.commons.model.{BankId, CustomerId}
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteBankCascade {

  def delete(bankId: BankId): Boolean = {
    MappedBankAccount.findAllByBankId(bankId.value).forall { i =>
      // Delete customer related to the account via account attribute "customer_number"
      DoobieAccountAttributeProvider.getAccountAttributesByBankSync(bankId.value)
        .filter(_.name == "customer_number").foreach { i =>
        val customerNumber = i.value
        CustomerX.customerProvider.vend.getCustomerByCustomerNumber(customerNumber, bankId).map( i =>
          DeleteCustomerCascade.delete(CustomerId(i.customerId))
        )
      }
      // Delete customer related to the account
      DoobieCustomerAccountLinkProvider.findByAccountIdSync(i.accountId.value).forall(i =>
        DeleteCustomerCascade.delete(CustomerId(i.customerId))
      )
      // Delete account
      DeleteAccountCascade.delete(i.bankId, i.accountId)
    } && deleteBank(bankId)
  }
  
  def atomicDelete(bankId: BankId): Box[Boolean] = databaseAtomicTask {
    delete(bankId) match {
      case true =>
        Full(true)
      case false =>
        DB.rollback(DefaultConnectionIdentifier)
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }

  private def deleteBank(bankId: BankId): Boolean = {
    MappedBank.deleteByBankId(bankId.value)
  }
  
  
}
