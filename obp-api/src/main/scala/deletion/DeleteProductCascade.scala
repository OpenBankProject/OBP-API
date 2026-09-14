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

import code.api.APIFailureNewStyle
import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.model.dataAccess.MappedBankAccount
import code.productAttributeattribute.MappedProductAttribute
import code.productfee.ProductFee
import code.products.MappedProduct
import com.openbankproject.commons.model.{BankId, ProductCode}
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteProductCascade {

  def delete(bankId: BankId, code: ProductCode): Boolean = {
    val doneTasks =
      deleteAccounts(bankId, code) ::
        deleteProductAttributes(bankId, code) ::
        deleteProductAttributeDefinitions(bankId, code) ::
        deleteProduct(bankId, code) ::
        deleteProductFee(bankId, code) ::
        Nil
    doneTasks.forall(_ == true)
  }
  
  def atomicDelete(bankId: BankId, code: ProductCode): Box[Boolean] = databaseAtomicTask {
    delete(bankId, code) match {
      case true =>
        Full(true)
      case false =>
        DB.rollback(DefaultConnectionIdentifier)
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }

  private def deleteProductAttributes(bankId: BankId, code: ProductCode): Boolean = {
    MappedProductAttribute.findAll(
      By(MappedProductAttribute.mBankId, bankId.value),
      By(MappedProductAttribute.mCode, code.value)
    ) map {
      attribute =>
        MappedProductAttribute.bulkDelete_!!(By(MappedProductAttribute.mProductAttributeId, attribute.productAttributeId))
    } forall (_ == true)
  }
  private def deleteProductAttributeDefinitions(bankId: BankId, code: ProductCode): Boolean = {
    AttributeDefinition.findAll(
      By(AttributeDefinition.BankId, bankId.value),
      By(AttributeDefinition.Category, code.value)
    ) map {
      definition =>
        AttributeDefinition.bulkDelete_!!(By(AttributeDefinition.AttributeDefinitionId, definition.attributeDefinitionId))
    } forall (_ == true)
  }
  private def deleteAccounts(bankId: BankId, code: ProductCode): Boolean = {
    MappedBankAccount.findAll(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.kind, code.value)
    ) map {
      account => DeleteAccountCascade.delete(account.bankId, account.accountId)
    } forall (_ == true)
  }
  private def deleteProduct(bankId: BankId, code: ProductCode): Boolean = {
    MappedProduct.bulkDelete_!!(
      By(MappedProduct.mBankId, bankId.value),
      By(MappedProduct.mCode, code.value)
    )
  }
  private def deleteProductFee(bankId: BankId, code: ProductCode): Boolean = {
    ProductFee.bulkDelete_!!(
      By(ProductFee.BankId, bankId.value),
      By(ProductFee.ProductCode, code.value)
    )
  }

}
