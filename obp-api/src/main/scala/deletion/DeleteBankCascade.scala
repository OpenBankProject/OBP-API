package deletion

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import com.openbankproject.commons.model.{AccountId, BankId}
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteBankCascade {

  def delete(bankId: BankId): Boolean = {
    MappedBankAccount.findAll(By(MappedBankAccount.bank, bankId.value))
      .forall(i => DeleteAccountCascade.delete(i.bankId, i.accountId)) && deleteBank(bankId)
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
