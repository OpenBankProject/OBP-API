package code.sandbox

import java.text.SimpleDateFormat
import code.bankconnectors.Connector
import code.model.dataAccess.{MappedAccountHolder, ViewImpl, OBPUser}
import code.model._
import code.views.Views
import net.liftweb.common.{Loggable, Full, Failure, Box}
import net.liftweb.mapper.By
import net.liftweb.util.SimpleInjector

object OBPDataImport extends SimpleInjector {

  val importer =  new Inject(buildOne _) {}

  def buildOne : OBPDataImport = LocalConnectorDataImport

}

//TODO: refactor Saveable
trait Saveable[T] {
  val value : T
  def save() : Unit
}


trait OBPDataImport extends Loggable {
  val datePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  val dateFormat = new SimpleDateFormat(datePattern)

  type BankType <: Bank
  type AccountType <: BankAccount
  type MetadataType <: OtherBankAccountMetadata
  type TransactionType
  type AccountOwnerEmail = String

  /**
   * Takes a list of boxes and returns a list of the content of the boxes if all boxes are Full, or returns
   * the first failure
   *
   * TODO: handle Empty boxes
   */
  protected def dataOrFirstFailure[T](boxes : List[Box[T]]) = {
    val firstFailure = boxes.collectFirst{case f: Failure => f}
    firstFailure match {
      case Some(f) => f
      case None => Full(boxes.flatten) //no failures, so we can return the results
    }
  }

  protected def createBanks(data : SandboxDataImport) = {
    val existing = data.banks.flatMap(b => Connector.connector.vend.getBanks)

    val allIds = data.banks.map(_.id)
    val emptyIds = allIds.filter(_.isEmpty)
    val uniqueIds = data.banks.map(_.id).distinct
    val duplicateIds = allIds diff uniqueIds

    if(!existing.isEmpty) {
      val existingIds = existing.map(_.bankId.value)
      Failure(s"Bank(s) with id(s) $existingIds already exist (and may have different non-id [e.g. short_name] values).")
    } else if (!emptyIds.isEmpty){
      Failure(s"Bank(s) with empty ids are not allowed")
    } else if(!duplicateIds.isEmpty) {
      Failure(s"Banks must have unique ids. Duplicates found: $duplicateIds")
    } else {
      createSaveableBanks(data.banks)
    }
  }

  protected def createSaveableBanks(data : List[SandboxBankImport]) : Box[List[Saveable[BankType]]]

  //TODO: remove dependency on OBPUser
  protected def createUsers(data : SandboxDataImport) : Box[List[Saveable[OBPUser]]] = {
    val existing = data.users.flatMap(u => OBPUser.find(By(OBPUser.email, u.email)))
    val allEmails = data.users.map(_.email)
    val duplicateEmails = allEmails diff allEmails.distinct

    if(!existing.isEmpty) {
      val existingEmails = existing.map(_.email.get)
      Failure(s"User(s) with email(s) $existingEmails already exist (and may be different (e.g. different display_name)")
    } else if(!duplicateEmails.isEmpty) {
      Failure(s"Users must have unique emails: Duplicates found: $duplicateEmails")
    }else {

      val obpUsers = data.users.map(u => {
        OBPUser.create
          .email(u.email)
          .lastName(u.display_name)
          .password(u.password)
          .validated(true)
      })

      val validationErrors = obpUsers.flatMap(_.validate)

      if(!validationErrors.isEmpty) {
        Failure(s"Errors: ${validationErrors.map(_.msg)}")
      } else {

        def asSaveable(u : OBPUser) = new Saveable[OBPUser] {
          val value = u
          def save() = u.save()
        }

        Full(obpUsers.map(asSaveable))
      }
    }
  }

  protected def createAccounts(data : SandboxDataImport, banks : List[BankType], users : List[OBPUser]) : Box[List[(Saveable[AccountType], List[Saveable[ViewImpl]], List[AccountOwnerEmail])]] = {

    val banksNotSpecifiedInImport = data.accounts.flatMap(acc => {
      if(data.banks.exists(b => b.id == acc.bank)) None
      else Some(acc.bank)
    })

    val emptyAccountIds = data.accounts.filter(acc => acc.id.isEmpty)

    case class AccountIdentifier(id : String, bank : String)
    case class AccountNumberForBank(number : String, bank : String)
    val ids = data.accounts.map(acc => AccountIdentifier(acc.id, acc.bank))
    val duplicateIds = ids diff ids.distinct

    val numbers = data.accounts.map(acc =>AccountNumberForBank(acc.number, acc.bank))
    val duplicateNumbers = numbers diff numbers.distinct

    val existing = data.accounts.flatMap(acc => {
      Connector.connector.vend.getBankAccount(BankId(acc.bank), AccountId(acc.id))
    })

    if(!banksNotSpecifiedInImport.isEmpty) {
      Failure(s"Error: one or more accounts specified are for" +
        s" banks not specified in the import data. Unspecified banks: $banksNotSpecifiedInImport)")
    } else if (emptyAccountIds.nonEmpty){
      Failure(s"Error: one or more accounts has an empty id")
    } else if (duplicateIds.nonEmpty){
      val duplicateMsg = duplicateIds.map(d => s"(bank id ${d.bank}, account id: ${d.id})").mkString(",")
      Failure(s"Error: accounts at the same bank may not share an id: $duplicateMsg")
    } else if(duplicateNumbers.nonEmpty){
      val duplicateMsg = duplicateNumbers.map(d => s"(bank id ${d.bank}, account number: ${d.number})").mkString(",")
      Failure(s"Error: accounts at the same bank may not share account numbers: $duplicateMsg")
    } else if(existing.nonEmpty) {
      val existingAccountAndBankIds = existing.map(e => (s"(account id: ${e.accountId.value} bank id: ${e.bankId.value})").mkString(","))
      Failure(s"Account(s) to be imported already exist: $existingAccountAndBankIds")
    } else {
      createSaveableAccountResults(data, banks, users)
    }
  }

  protected def createSaveableAccountResults(data : SandboxDataImport, banks : List[BankType], users : List[OBPUser]) : Box[List[(Saveable[AccountType], List[Saveable[ViewImpl]], List[AccountOwnerEmail])]]

  protected def createSaveableTransactionsAndMetas(transactions : List[SandboxTransactionImport], createdBanks : List[BankType], createdAccounts : List[AccountType]):
    Box[(List[Saveable[TransactionType]], List[Saveable[MetadataType]])]

  protected def tmpCreateTransactionsAndMetas(data : SandboxDataImport, createdBanks : List[BankType], createdAccounts : List[AccountType]) : Box[(List[Saveable[TransactionType]], List[Saveable[MetadataType]])] = {
    def createdAccount(transaction : SandboxTransactionImport) =
      createdAccounts.find(acc =>
        acc.accountId == AccountId(transaction.this_account.id) &&
          acc.bankId == BankId(transaction.this_account.bank))

    val transactionsWithNoAccountSpecifiedInImport = data.transactions.filter(createdAccount(_).isDefined)
    val transactionsWithEmptyIds = data.transactions.filter(_.id.isEmpty)

    case class TransactionIdentifier(id : String, account : String, bank : String)

    val identifiers = data.transactions.map(t => TransactionIdentifier(t.id, t.this_account.id, t.this_account.bank))
    val duplicateIdentifiers = identifiers diff identifiers.distinct

    val existing = data.transactions.filter(t => {
      Connector.connector.vend.getTransaction(BankId(t.this_account.bank), AccountId(t.this_account.id), TransactionId(t.id)).isDefined
    })

    if(transactionsWithNoAccountSpecifiedInImport.nonEmpty) {
      val identifiers = transactionsWithNoAccountSpecifiedInImport.map(
        t => s"(transaction id ${t.id}, account id ${t.this_account.id}, bank id ${t.this_account.bank})")
      Failure(s"Transaction(s) exist with accounts/banks not specified in import data: $identifiers")
    } else if (transactionsWithEmptyIds.nonEmpty) {
      Failure(s"Transaction(s) exist with empty ids")
    } else if(duplicateIdentifiers.nonEmpty) {
      val duplicatesMsg = duplicateIdentifiers.map(i => s"(transaction id : ${i.id}, account id: ${i.account}, bank id: ${i.bank})").mkString(",")
      Failure(s"Transactions for an account must have unique ids. Violations: ${duplicatesMsg} ")
    } else if(existing.nonEmpty) {
      val existingIdentifiers = existing.map { t =>
        s"(transaction id: ${t.id} account id : ${t.this_account.id} bank id : ${t.this_account.bank})"
      }
      Failure(s"Some transactions already exist: ${existingIdentifiers.mkString("[", ",", "]")}")
    } else {
      //TODO validate numbers and dates in one place
      createSaveableTransactionsAndMetas(data.transactions, createdBanks, createdAccounts)
    }
  }

  //TODO: have this return a saveable something?
  protected def setAccountOwner(owner : AccountOwnerEmail, account: AccountType, createdUsers: List[OBPUser]) : Unit

  /**
   * @param data
   * @return A full box if the import worked, or else a failure describing what went wrong
   */
  def importData(data: SandboxDataImport) : Box[Unit] = {
    for {
      banks <- createBanks(data)
      users <- createUsers(data)
      accountResults <- createAccounts(data, banks.map(_.value), users.map(_.value))
      (transactions, metadatas) <- tmpCreateTransactionsAndMetas(data, banks.map(_.value), accountResults.map(_._1.value))
    } yield {
      banks.foreach(_.save())

      users.foreach(_.save())

      accountResults.foreach {
        case (account, views, accOwnerEmails) =>
          account.save()
          views.foreach(_.save())

          views.map(_.value).filterNot(_.isPublic).foreach(v => {
            //grant the owner access to non-public views
            //this should always find the owners as that gets verified at an earlier stage, but it's not perfect this way
            val accOwners = users.map(_.value).filter(u => accOwnerEmails.exists(email => u.email.get == email)).flatMap(_.user.obj)
            accOwners.foreach(Views.views.vend.addPermission(v.uid, _))
          })

          accOwnerEmails.foreach(setAccountOwner(_, account.value, users.map(_.value)))
      }

      transactions.foreach(_.save())
      metadatas.foreach(_.save())
    }
  }

}


case class SandboxBankImport(
  id : String,
  short_name : String,
  full_name : String,
  logo : String,
  website : String)

case class SandboxUserImport(
  email : String,
  password : String,
  display_name : String)

case class SandboxAccountImport(
  id : String,
  bank : String,
  label : String,
  number : String,
  `type` : String,
  balance : SandboxBalanceImport,
  IBAN : String,
  owners : List[String],
  generate_public_view : Boolean)

case class SandboxBalanceImport(
  currency : String,
  amount : String)

case class SandboxTransactionImport(
  id : String,
  this_account : SandboxAccountIdImport,
  counterparty : Option[SandboxTransactionCounterparty],
  details : SandboxAccountDetailsImport)

case class SandboxTransactionCounterparty(
  name : Option[String],
  account_number : Option[String])

case class SandboxAccountIdImport(
  id : String,
  bank : String)

case class SandboxAccountDetailsImport(
  `type` : String,
  description : String,
  posted : String,
  completed : String,
  new_balance : String,
  value : String)

case class SandboxDataImport(
  banks : List[SandboxBankImport],
  users : List[SandboxUserImport],
  accounts : List[SandboxAccountImport],
  transactions : List[SandboxTransactionImport])