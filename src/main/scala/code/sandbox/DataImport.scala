package code.sandbox

import code.api.APIFailure
import code.metadata.counterparties.{MongoCounterparties, Metadata}
import code.model.{AccountId, BankId}
import code.model.dataAccess._
import com.mongodb.QueryBuilder
import net.liftweb.common._
import net.liftweb.mapper.By
import java.util.Date
import net.liftweb.util.Helpers._


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
        Failure(s"Banks must have unique ids. Duplicates found: $duplicateIds")
      }else {
        val hostedBanks = data.banks.map(b => {
          HostedBank.createRecord
            .permalink(b.id)
            .name(b.full_name)
            .alias(b.short_name)
            .website(b.website)
            .logoURL(b.logo)
            .national_identifier(b.id) //this needs to match up with what goes in the OBPEnvelopes
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
          createdBanks.find(createdBank => createdBank.permalink.get == acc.bank)

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
            for {
              hBank <- hostedBank
              balance <- tryo{BigDecimal(acc.balance.amount)} ?~ s"Invalid balance: ${acc.balance.amount}"
            } yield {
              val account = Account.createRecord
                .permalink(acc.id)
                .bankID(hBank.id.get)
                .label(acc.label)
                .currency(acc.balance.currency)
                .balance(balance)
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
              (account, views)
            }
          })

          if(accountsAndViews.size != data.accounts.size) {
            logger.error("Couldn't create an Account for all accounts in data import")
            val failMsg = "Unknown error"
            //TODO: give a better message depending on the failure, perhaps a 400
            ParamFailure(failMsg, APIFailure(failMsg, 500))
          } else {
            Full( accountsAndViews)
          }
        }
      }

    }

    //TODO: return metadata too? it will need to be saved as well
    def createTransactions(createdAccounts : List[Account]) : Box[List[(OBPEnvelope, Metadata)]] = {

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
        val existing = data.transactions.flatMap(t => {
          for {
            account <- Box(createdAccount(t))
            accountEnvelopesQuery = account.transactionsForAccount
            queryWithTransId = accountEnvelopesQuery.put("transactionId").is(t.id)
            env <- OBPEnvelope.find(queryWithTransId.get)
          } yield (t, env)
        })

        if(!existing.isEmpty) {
          val existingIdentifiers = existing.map {
            case(t, env) => s"transaction id: ${t.id} account id : ${t.this_account.id} bank id : ${t.this_account.bank}"
          }
          Failure(s"Some transactions already exist: $existingIdentifiers")
        } else {

          val envsAndMeta : List[(OBPEnvelope, Metadata)] = data.transactions.flatMap(t => {

            val metadataOpt = t.counterparty match {
              case Some(counter) => {
                //TODO: verify counterparty was in data import
                val counterPartyAccount = createdAccounts.find(a => a.accountId == AccountId(counter.id) && a.bankId == BankId(counter.bank))

                counterPartyAccount.map(a => {
                  val existingMeta = Metadata.find(QueryBuilder.start("originalPartyBankId").is(t.this_account.bank)
                    .put("originalPartyAccountId").is(t.this_account.id)
                    .put("holder").is(a.label.get).get())

                  existingMeta.getOrElse{
                    Metadata.createRecord
                      .holder(a.label.get)
                      .publicAlias(MongoCounterparties.newPublicAliasName(BankId(t.this_account.bank), AccountId(t.this_account.id)))
                  }

                })
              }
              case None => {
                Some(Metadata.createRecord
                  .holder(t.details.description)
                  .publicAlias(MongoCounterparties.newPublicAliasName(BankId(t.this_account.bank), AccountId(t.this_account.id))))
              }
            }

            metadataOpt.flatMap(m => {

              for {
                createdAcc <- createdAccounts.find(a => a.bankId == BankId(t.this_account.bank) && a.accountId == AccountId(t.this_account.id))
              } yield {

                //TODO: metadata will get gen'd automatically?? with wrong values???

                val obpThisAccountBank = OBPBank.createRecord
                  .national_identifier(createdAcc.bankNationalIdentifier)

                val obpThisAccount = OBPAccount.createRecord
                  .holder(createdAcc.holder.get)
                  .number(createdAcc.number.get)
                  .kind(createdAcc.kind.get)
                  .bank(obpThisAccountBank)

                val obpOtherAccount = OBPAccount.createRecord
                  .holder(m.holder.get)

                val newBalance = OBPBalance.createRecord
                  .amount(BigDecimal("0.00")) //TODO: verify new balance as bigdecimal and use it
                  .currency(createdAcc.currency.get)

                val transactionValue = OBPValue.createRecord
                  .amount(BigDecimal("0.00")) //TODO: verify value as bigdecimal and use it
                  .currency(createdAcc.currency.get)

                val obpDetails = OBPDetails.createRecord
                  .completed(new Date()) //TODO: verify date and use it
                  .posted(new Date()) //TODO: verify date and use it
                  .kind(t.details.`type`)
                  .label(t.details.description)
                  .new_balance(newBalance)
                  .value(transactionValue)


                val obpTransaction = OBPTransaction.createRecord
                  .details(obpDetails)
                  .this_account(obpThisAccount)
                  .other_account(obpOtherAccount)

                val env = OBPEnvelope.createRecord
                  .transactionId(t.id)
                  .obp_transaction(obpTransaction)

                (env, m)
              }
            })

          })

          //todo: if >0 metadata missing, does it always means the counterparty was not specified in data import?

          //TODO: create obpenvelopes (and metadatas?)
          Failure("TODO")
          Full(envsAndMeta)
        }

      }

      //Failure("TODO")
    }

    for {
      banks <- createBanks()
      users <- createUsers()
      accountsAndViews <- createAccountsAndViews(banks, users)
      transactionsAndMetas <- createTransactions(accountsAndViews.map(_._1))
    } yield {

      //import format has been shown to be correct, now we can save everything we created
      banks.foreach(_.save(true))
      users.foreach(_.save())
      accountsAndViews.foreach{
        case (account, views) =>
          account.save(true)
          views.foreach(_.save)
      }
      transactionsAndMetas.foreach {
        case (trans, meta) => {
          meta.save(true)
          trans.save(true)
        }
      }

      Full(Unit)
    }
  }

}
