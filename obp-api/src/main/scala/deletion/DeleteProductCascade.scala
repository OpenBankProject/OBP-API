package deletion

import code.api.APIFailureNewStyle
import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.model.dataAccess.MappedBankAccount
import code.productAttributeattribute.MappedProductAttribute
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

}
