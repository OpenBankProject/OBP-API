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

import code.accountattribute.DoobieAccountAttributeProvider
import code.api.APIFailureNewStyle
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.DynamicData.{DynamicData, DynamicDataAccess, DynamicDataProvider}
import code.api.util.NewStyle
import code.customer.CustomerX
import code.customeraccountlinks.DoobieCustomerAccountLinkProvider
import code.dynamicEntity.DynamicEntityProvider
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
    } && (isSystemSpaceMarker(bankId) || deleteDynamicEntities(bankId)) && deleteBank(bankId)
  }

  /**
   * Dynamic entities of the system space are stored under this marker, so a bank that has it as its id cannot have
   * dynamic entities of its own, and deleting the bank must not touch the system space.
   */
  private def isSystemSpaceMarker(bankId: BankId): Boolean = bankId.value == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  
  def atomicDelete(bankId: BankId): Box[Boolean] = databaseAtomicTask {
    delete(bankId) match {
      case true =>
        Full(true)
      case false =>
        // Throws, so databaseAtomicTask restores what delete has removed.
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }

  /**
   * The bank's dynamic entities with their records (whoever owns them), access rows, entitlements and roles, the
   * ZZ_BAK_ copies among them. With the bank gone their endpoints answer BankNotFound, so nothing else could
   * remove them.
   */
  private def deleteDynamicEntities(bankId: BankId): Boolean = {
    val space = Some(bankId.value)
    DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities(space, returnBothBankAndSystemLevel = false).forall { entity =>
      val recordsDeleted = DynamicDataProvider.connectorMethodProvider.vend.getAllCommunity(space, entity.entityName).forall { record =>
        record.dynamicDataId.exists(id => DynamicDataProvider.connectorMethodProvider.vend.deleteCommunity(space, entity.entityName, id).openOr(false))
      }
      recordsDeleted && entity.dynamicEntityId.exists(id => NewStyle.function.deleteDynamicEntityNow(space, id).openOr(false))
    } && deleteRecordsWithoutDefinition(bankId)
  }

  /**
   * What is left of the bank's records once its definitions are gone: a definition can be deleted while personal
   * records of it remain, so some records, and the access rows on them, may have no definition.
   */
  private def deleteRecordsWithoutDefinition(bankId: BankId): Boolean = {
    DynamicData.deleteAllAtBank(bankId.value)
    DynamicDataAccess.deleteAllAtBank(bankId.value)
    true
  }

  private def deleteBank(bankId: BankId): Boolean = {
    MappedBank.deleteByBankId(bankId.value)
  }
  
  
}
