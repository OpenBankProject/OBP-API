package code.sandbox

import code.model._
import net.liftweb.common.Box

object DummyDataImport extends OBPDataImport {

  override type BankType = Bank
  override type MetadataType = OtherBankAccountMetadata
  override type ViewType = View
  override type TransactionType = TransactionUUID
  override type AccountType = BankAccount

  /**
   * Create a public view for account with BankId @bankId and AccountId @accountId that can be saved.
   */
  override protected def createSaveablePublicView(bankId: BankId, accountId: AccountId): Saveable[ViewType] = ???

  /**
   * Create an owner view for account with BankId @bankId and AccountId @accountId that can be saved.
   */
  override protected def createSaveableOwnerView(bankId: BankId, accountId: AccountId): Saveable[ViewType] = ???

  /**
   * Creates a saveable transaction object. This method assumes the transaction has passed
   * preliminary validation checks.
   */
  override protected def createSaveableTransaction(t: SandboxTransactionImport, createdBanks: List[DummyDataImport.BankType], createdAccounts: List[AccountType]): Box[Saveable[TransactionType]] = ???

  /**
   * Create banks that can be saved. This method assumes the banks in @data have passed validation checks and are allowed
   * to be created as is.
   */
  override protected def createSaveableBanks(data: List[SandboxBankImport]): Box[List[Saveable[DummyDataImport.BankType]]] = ???

  /**
   * Creates an account that can be saved. This methods assumes that @acc has passed validatoin checks and is allowed
   * to be created as is.
   */
  override protected def createSaveableAccount(acc: SandboxAccountImport, banks: List[DummyDataImport.BankType]): Box[Saveable[AccountType]] = ???

}
