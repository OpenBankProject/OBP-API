package code.sandbox

import code.api.APIFailure
import code.model.{AccountId, BankId}
import code.model.dataAccess._
import net.liftweb.common._
import net.liftweb.mapper.By


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
  amount : Double)

case class SandboxTransactionImport(
  id : String,
  this_account : SandboxAccountIdImport,
  counterparty : Option[SandboxAccountIdImport],
  details : SandboxAccountDetailsImport)

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

object DataImport extends Loggable {

  /**
   * @param data
   * @return A full box if the import worked, or else a failure describing what went wrong
   */
  def importData(data : SandboxDataImport) : Box[Unit] = {

    def createBanks() : Box[List[HostedBank]] = {
      val existing = data.banks.flatMap(b => HostedBank.find(BankId(b.id)))

      val allIds = data.banks.map(_.id)
      val emptyIds = allIds.filter(_.isEmpty)
      val uniqueIds = data.banks.map(_.id).distinct
      val duplicateIds = allIds diff uniqueIds

      if(!existing.isEmpty) {
        val existingIds = existing.map(_.permalink.get)
        Failure(s"Bank(s) with id(s) $existingIds already exist (and may have different non-id [e.g. short_name] values).")
      } else if (!emptyIds.isEmpty){
        Failure(s"Bank(s) with empty ids are not allowed")
      } else if(!duplicateIds.isEmpty) {
        Failure(s"Banks must have unique ids. Duplicated found: $duplicateIds")
      }else {
        val hostedBanks = data.banks.map(b => {
          HostedBank.createRecord
            .permalink(b.id)
            .name(b.full_name)
            .alias(b.short_name)
            .website(b.website)
            .logoURL(b.logo)
        })

        val validationErrors = hostedBanks.flatMap(_.validate)

        if(!validationErrors.isEmpty) {
          Failure(s"Errors: ${validationErrors.map(_.msg)}")
        } else {
          Full(hostedBanks)
        }
      }
    }

    def createUsers() : Box[List[OBPUser]] = {
      val existing = data.users.flatMap(u => OBPUser.find(By(OBPUser.email, u.email)))
      if(!existing.isEmpty) {
        val existingEmails = existing.map(_.email.get)
        Failure(s"User(s) with email(s) $existingEmails already exist (and may be different (e.g. different display_name)")
      } else {

        val obpUsers = data.users.map(u => {
          OBPUser.create
            .email(u.email)
            .lastName(u.display_name)
            .password(u.password)
        })

        val validationErrors = obpUsers.flatMap(_.validate)

        if(!validationErrors.isEmpty) {
          Failure(s"Errors: ${validationErrors.map(_.msg)}")
        } else {
          Full(obpUsers)
        }
      }
    }

    //returns unsaved accounts and unsaved views for those accounts
    def createAccountsAndViews(createdBanks : List[HostedBank], createdUsers : List[OBPUser]) : Box[List[(Account, List[ViewImpl])]] = {

      val banksNotSpecifiedInImport = data.accounts.flatMap(acc => {
        if(data.banks.exists(b => b.id == acc.bank)) None
        else Some(acc.bank)
      })

      if(!banksNotSpecifiedInImport.isEmpty) {
        Failure(s"Error: one or more accounts specified are for" +
          s" banks not specified in the import data. Unspecified banks: $banksNotSpecifiedInImport)")
      } else {

        def getHostedBank(acc : SandboxAccountImport) =
          createdBanks.find(created => created.permalink.get == acc.id)

        val existing = data.accounts.flatMap(acc => {
          val hostedBank = getHostedBank(acc)
          hostedBank match {
            case Some(hBank) => {
              val existing = hBank.getAccount(AccountId(acc.id))
              existing.toOption
            }
            case None => None //this is bad (no bank found), but will get handled later on in another check
          }
        })

        if(!existing.isEmpty) {
          val existingAccountAndBankIds = existing.map(e => (s"account id: ${e.permalink.get} bank id: ${e.bankId.value}}"))
          Failure(s"Account(s) to be imported already exist: $existingAccountAndBankIds")
        } else {
          val accountsAndViews = data.accounts.flatMap(acc => {
            val hostedBank = getHostedBank(acc)
            for(hBank <- hostedBank)
            yield {
              val account = Account.createRecord
                .permalink(acc.id)
                .bankID(hBank.id.get)
                .label(acc.label)
                .currency(acc.balance.currency)
                .balance(acc.balance.amount)
                .number(acc.number)
                .kind(acc.`type`)
                .iban(acc.IBAN)

              val bankId = BankId(acc.bank)
              val accountId = AccountId(acc.id)

              val ownerView = Some(ViewImpl.unsavedOwnerView(bankId, accountId, "Owner View"))

              val publicView =
                if(acc.generate_public_view) Some(ViewImpl.createAndSaveDefaultPublicView(bankId, accountId, "Public View"))
                else None

              val views = List(ownerView, publicView).flatten
              (account, List(views))
            }
          })

          if(accountsAndViews.size != data.accounts.size) {
            logger.error("Couldn't create an Account for all accounts in data import")
            val failMsg = "Server error"
            ParamFailure(failMsg, APIFailure(failMsg, 500))
          } else {
            Full(accountsAndViews)
          }
        }
      }

    }

    //TODO: return metadata too? it will need to be saved as well
    def createTransactions(createdAccounts : List[Account]) : Box[List[OBPEnvelope]] = {

      def createdAccount(transaction : SandboxTransactionImport) =
        createdAccounts.find(acc =>
          acc.accountId == AccountId(transaction.this_account.id) &&
            acc.bankId == BankId(transaction.this_account.bank))

      val transactionsWithNoAccountSpecifiedInImport = data.transactions.flatMap(t => {
        val createdAcc = createdAccount(t)
        if(createdAcc.isDefined) Some(t)
        else None
      })

      if(!transactionsWithNoAccountSpecifiedInImport.isEmpty) {
        val identifiers = transactionsWithNoAccountSpecifiedInImport.map(
          t => s"transaction id ${t.id}, account id ${t.this_account.id}, bank id ${t.this_account.bank}")
        Failure(s"Transaction(s) exist with accounts/banks not specified in import data: $identifiers")
      } else {
        //TODO: go via getting Account, then getting envs for that account
        val existing = data.transactions.flatMap(t => {
          for {
            account <- createdAccount(t)
            accountEnvelopesQuery <- account.transactionsForAccount
            queryWithId = accountEnvelopesQuery
            found <- OBPEnvelope.find(queryWithId)
          } yield {

          }
        })
      }

      Failure("TODO")
    }

    for {
      banks <- createBanks()
      users <- createUsers()
      accountsAndViews <- createAccountsAndViews(banks, users)
      transactions <- createTransactions(accountsAndViews.map(_._1))
    } yield {

      //import format has been shown to be correct, now we can save everything we created
      banks.foreach(_.save(true))
      users.foreach(_.save())
      accountsAndViews.foreach{
        case (account, views) =>
          account.save(true)
          views.foreach(_.save)
      }
      transactions.foreach(t => {
        t.save(true)
        //TODO: metadata?
      })

      Full(Unit)
    }
  }

}
