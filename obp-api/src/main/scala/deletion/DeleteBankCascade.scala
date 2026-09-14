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

import code.accountattribute.MappedAccountAttribute
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.customer.CustomerX
import code.customeraccountlinks.CustomerAccountLink
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import com.openbankproject.commons.model.{BankId, CustomerId}
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteBankCascade {

  def delete(bankId: BankId): Boolean = {
    MappedBankAccount.findAll(By(MappedBankAccount.bank, bankId.value)).forall { i =>
      // Delete customer related to the account via account attribute "customer_number"
      MappedAccountAttribute.findAll(
        By(MappedAccountAttribute.mBankIdId, bankId.value)
      ).filter(_.name == "customer_number").foreach { i =>
        val customerNumber = i.value
        CustomerX.customerProvider.vend.getCustomerByCustomerNumber(customerNumber, bankId).map( i =>
          DeleteCustomerCascade.delete(CustomerId(i.customerId))
        )
      }
      // Delete customer related to the account
      CustomerAccountLink.findAll(By(CustomerAccountLink.AccountId, i.accountId.value)).forall(i => 
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
    MappedBank.bulkDelete_!!(
      By(MappedBank.permalink, bankId.value)
    )
  }
  
  
}
