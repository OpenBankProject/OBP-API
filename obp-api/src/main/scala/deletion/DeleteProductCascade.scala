package deletion

import code.api.APIFailureNewStyle
import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.model.dataAccess.MappedBankAccount
import code.productattribute.DoobieProductAttributeProvider
import code.productfee.ProductFee
import code.products.MappedProduct
import com.openbankproject.commons.model.{BankId, ProductCode}
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
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
    DoobieProductAttributeProvider.deleteProductAttributesByBankAndCode(bankId.value, code.value)
  }
  private def deleteProductAttributeDefinitions(bankId: BankId, code: ProductCode): Boolean = {
    AttributeDefinition.findAllByBankIdAndCategory(bankId.value, code.value) map {
      definition =>
        AttributeDefinition.deleteByAttributeDefinitionId(definition.attributeDefinitionId)
    } forall (_ == true)
  }
  private def deleteAccounts(bankId: BankId, code: ProductCode): Boolean = {
    MappedBankAccount.findAllByBankIdAndKind(bankId.value, code.value
    ) map {
      account => DeleteAccountCascade.delete(account.bankId, account.accountId)
    } forall (_ == true)
  }
  private def deleteProduct(bankId: BankId, code: ProductCode): Boolean = {
    MappedProduct.delete(bankId.value, code.value)
  }
  private def deleteProductFee(bankId: BankId, code: ProductCode): Boolean = {
    ProductFee.deleteByBankIdAndProductCode(bankId.value, code.value)
  }

}
