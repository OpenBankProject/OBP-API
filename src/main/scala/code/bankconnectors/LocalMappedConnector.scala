package code.bankconnectors

import code.metadata.counterparties.{Counterparties, MappedCounterpartyMetadata}
import code.model._
import code.model.dataAccess.{MappedBankAccount, MappedAccountHolder, MappedBank}
import net.liftweb.common.{Failure, Full, Box}
import net.liftweb.mapper.By

object LocalMappedConnector extends Connector {
  //gets a particular bank handled by this connector
  override def getBank(bankId: BankId): Box[Bank] =
    MappedBank.find(By(MappedBank.permalink, bankId.value))

  //gets banks handled by this connector
  override def getBanks: List[Bank] =
    MappedBank.findAll

  override def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId): Box[Transaction] = {
    MappedTransaction.find(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountID.value),
      By(MappedTransaction.transactionId, transactionId.value)).flatMap(_.toTransaction)
  }

  override def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
    Full(MappedTransaction.findAll(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountID.value)).flatMap(_.toTransaction))
  }

  override def getBankAccount(bankId: BankId, accountId: AccountId): Box[BankAccount] =
    MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.theAccountId, accountId.value))

  //gets the users who are the legal owners/holders of the account
  override def getAccountHolders(bankId: BankId, accountID: AccountId): Set[User] =
    MappedAccountHolder.findAll(
      By(MappedAccountHolder.accountBankPermalink, bankId.value),
      By(MappedAccountHolder.accountPermalink, accountID.value)).map(accHolder => accHolder.user.obj).flatten.toSet


  def getOtherBankAccount(thisAccountBankId : BankId, thisAccountId : AccountId, metadata : OtherBankAccountMetadata) : Box[OtherBankAccount] = {
    //because we don't have a db backed model for OtherBankAccounts, we need to construct it from an
    //OtherBankAccountMetadata and a transaction
    for { //find a transaction with this counterparty
      t <- MappedTransaction.find(
        By(MappedTransaction.bank, thisAccountBankId.value),
        By(MappedTransaction.account, thisAccountId.value),
        By(MappedTransaction.counterpartyAccountHolder, metadata.getHolder),
        By(MappedTransaction.counterpartyAccountNumber, metadata.getAccountNumber))
    } yield {
      //TODO: it's a bit counterproductive that creating the OtherBankAccount will load the metadata again
      new OtherBankAccount(
        //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
        id = metadata.metadataId,
        label = metadata.getHolder,
        nationalIdentifier = t.counterpartyNationalId.get,
        swift_bic = None,
        iban = t.getCounterpartyIban(),
        number = metadata.getAccountNumber,
        bankName = t.counterpartyBankName.get,
        kind = t.counterpartyAccountKind.get,
        originalPartyBankId = thisAccountBankId,
        originalPartyAccountId = thisAccountId
      )
    }
  }

  override def getOtherBankAccounts(bankId: BankId, accountID: AccountId): List[OtherBankAccount] =
    Counterparties.counterparties.vend.getMetadatas(bankId, accountID).flatMap(getOtherBankAccount(bankId, accountID, _))

  override def getOtherBankAccount(bankId: BankId, accountID: AccountId, otherAccountID: String): Box[OtherBankAccount] =
    Counterparties.counterparties.vend.getMetadata(bankId, accountID, otherAccountID).flatMap(getOtherBankAccount(bankId, accountID, _))

  override def getPhysicalCards(user: User): Set[PhysicalCard] =
    Set.empty

  override def getPhysicalCardsForBank(bankId: BankId, user: User): Set[PhysicalCard] =
    Set.empty
}
