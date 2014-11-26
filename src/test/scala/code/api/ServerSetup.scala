/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api.test

import bootstrap.liftweb.ToSchemify
import code.TestServer
import code.model._
import com.mongodb.QueryBuilder
import net.liftweb.mapper.{MetaMapper, Schemifier}
import org.scalatest._
import dispatch._
import net.liftweb.json.{Serialization, NoTypeHints}
import net.liftweb.common._
import code.bankconnectors.{OBPLimit, OBPOffset, Connector}
import net.liftweb.mongodb._
import code.model.dataAccess._
import java.util.Date
import _root_.net.liftweb.util._
import Helpers._
import scala.util.Random._
import scala.math.BigDecimal
import BigDecimal._

trait ServerSetup extends FeatureSpec with SendServerRequests
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with ShouldMatchers with Loggable{

  var server = TestServer
  implicit val formats = Serialization.formats(NoTypeHints)
  val h = Http
  def baseRequest = host(server.host, server.port)

  override def beforeEach() = {
    implicit val dateFormats = net.liftweb.json.DefaultFormats
    //create fake data for the tests

    //fake banks
    val banks = for{i <- 0 until 3} yield {
      createHostedBank(randomString(5))
    }

    //fake bank accounts
    val accounts = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createMongoAccountAndOwnerView(None, bank.bankId, AccountId(randomString(4)), randomString(4))
        }
      })

    accounts.foreach(account => {
      //create public view and another random view (owner view has already been created
      publicView(account.bankId, account.accountId)
      randomView(account.bankId, account.accountId)
    })

    //fake transactions
    accounts.foreach(account => {
      import java.util.Calendar

     val thisAccountBank = OBPBank.createRecord.
        IBAN(randomString(5)).
        national_identifier(account.nationalIdentifier).
        name(account.bankName)
      val thisAccount = OBPAccount.createRecord.
        holder(account.holder.get).
        number(account.accountNumber.get).
        kind(account.kind.get).
        bank(thisAccountBank)

      def add10Minutes(d: Date): Date = {
        val calendar = Calendar.getInstance
        calendar.setTime(d)
        calendar.add(Calendar.MINUTE, 10)
        calendar.getTime
      }

      val initialDate: Date = {
        val calendar = Calendar.getInstance
        calendar.setTime(new Date())
        calendar.add(Calendar.YEAR, -1)
        calendar.getTime
      }

      object InitialDateFactory{
        val calendar = Calendar.getInstance
        calendar.setTime(initialDate)
        def date: Date = {
          calendar.add(Calendar.HOUR, 10)
          calendar.getTime
        }
      }

      val NUM_TRANSACTIONS = 10

      for(i <- 0 until NUM_TRANSACTIONS){

        val otherAccountBank = OBPBank.createRecord.
          IBAN(randomString(5)).
          national_identifier(randomString(5)).
          name(randomString(5))

        val otherAccount = OBPAccount.createRecord.
          holder(randomString(5)).
          number(randomString(5)).
          kind(randomString(5)).
          bank(otherAccountBank)

        val transactionAmount = BigDecimal(nextDouble * 1000).setScale(2,RoundingMode.HALF_UP)

        val newBalance : OBPBalance = OBPBalance.createRecord.
          currency(account.accountCurrency.get).
          amount(account.accountBalance.get + transactionAmount)

        val newValue : OBPValue = OBPValue.createRecord.
          currency(account.accountCurrency.get).
          amount(transactionAmount)

        val details ={
          val postedDate = InitialDateFactory.date
          val completedDate = add10Minutes(postedDate)

          OBPDetails
          .createRecord
          .kind(randomString(5))
          .posted(postedDate)
          .other_data(randomString(5))
          .new_balance(newBalance)
          .value(newValue)
          .completed(completedDate)
          .label(randomString(5))
        }
        val transaction = OBPTransaction.createRecord.
          this_account(thisAccount).
          other_account(otherAccount).
          details(details)

        val env = OBPEnvelope.createRecord.
          obp_transaction(transaction).save
        account.accountBalance(newBalance.amount.get).lastUpdate(now).save
        env.save
      }

      //load all transactions for the account to generate the counterparty metadata
      Connector.connector.vend.getTransactions(account.bankId, account.accountId, OBPOffset(0), OBPLimit(NUM_TRANSACTIONS))

    })
  }

  override def afterEach() = {
    //drop the mongo Database after each test
    MongoDB.getDb(DefaultMongoIdentifier).foreach(_.dropDatabase())

    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == OBPUser || m == APIUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }

  private def createMongoAccountAndOwnerView(accountOwner: Option[User], bankId: BankId, accountId : AccountId, currency : String) : Account = {

    val q = QueryBuilder.start(HostedBank.permalink.name).is(bankId.value).get()
    val hostedBank = HostedBank.find(q).get

    val created = Account.createRecord.
      accountBalance(1000).
      holder(randomString(4)).
      accountNumber(randomString(4)).
      kind(randomString(4)).
      accountName(randomString(4)).
      permalink(accountId.value).
      bankID(hostedBank.id.get).
      accountLabel(randomString(4)).
      accountCurrency(currency).
      save

    val owner = ownerViewImpl(bankId, accountId)

    //give to user1 owner view
    if(accountOwner.isDefined) {
      ViewPrivileges.create.
        view(owner).
        user(accountOwner.get.apiId.value).
        save
    }

    created
  }

  def createAccountAndOwnerView(accountOwner: Option[User], bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    createMongoAccountAndOwnerView(accountOwner, bankId, accountId, currency)
  }

  def createPaymentTestBank() : Bank =
    createBank("payment-test-bank")

  private def createHostedBank(permalink : String) : HostedBank = {
    HostedBank.createRecord.
      name(randomString(5)).
      alias(randomString(5)).
      permalink(permalink).
      national_identifier(randomString(5)).
      save
  }

  def createBank(permalink : String) : Bank =
    createHostedBank(permalink)

  private def ownerViewImpl(bankId : BankId, accountId : AccountId) : ViewImpl =
    ViewImpl.createAndSaveOwnerView(bankId, accountId, randomString(3))

  def ownerView(bankId: BankId, accountId: AccountId) : View =
    ownerViewImpl(bankId, accountId)

  def publicView(bankId: BankId, accountId: AccountId) : View =
    ViewImpl.createAndSaveDefaultPublicView(bankId, accountId, randomString(3))

  def randomView(bankId: BankId, accountId: AccountId) : View =
    ViewImpl.create.
    name_(randomString(5)).
    description_(randomString(3)).
    permalink_(randomString(3)).
    isPublic_(false).
    bankPermalink(bankId.value).
    accountPermalink(accountId.value).
    usePrivateAliasIfOneExists_(false).
    usePublicAliasIfOneExists_(false).
    hideOtherAccountMetadataIfAlias_(false).
    canSeeTransactionThisBankAccount_(true).
    canSeeTransactionOtherBankAccount_(true).
    canSeeTransactionMetadata_(true).
    canSeeTransactionDescription_(true).
    canSeeTransactionAmount_(true).
    canSeeTransactionType_(true).
    canSeeTransactionCurrency_(true).
    canSeeTransactionStartDate_(true).
    canSeeTransactionFinishDate_(true).
    canSeeTransactionBalance_(true).
    canSeeComments_(true).
    canSeeOwnerComment_(true).
    canSeeTags_(true).
    canSeeImages_(true).
    canSeeBankAccountOwners_(true).
    canSeeBankAccountType_(true).
    canSeeBankAccountBalance_(true).
    canSeeBankAccountCurrency_(true).
    canSeeBankAccountLabel_(true).
    canSeeBankAccountNationalIdentifier_(true).
    canSeeBankAccountSwift_bic_(true).
    canSeeBankAccountIban_(true).
    canSeeBankAccountNumber_(true).
    canSeeBankAccountBankName_(true).
    canSeeBankAccountBankPermalink_(true).
    canSeeOtherAccountNationalIdentifier_(true).
    canSeeOtherAccountSWIFT_BIC_(true).
    canSeeOtherAccountIBAN_ (true).
    canSeeOtherAccountBankName_(true).
    canSeeOtherAccountNumber_(true).
    canSeeOtherAccountMetadata_(true).
    canSeeOtherAccountKind_(true).
    canSeeMoreInfo_(true).
    canSeeUrl_(true).
    canSeeImageUrl_(true).
    canSeeOpenCorporatesUrl_(true).
    canSeeCorporateLocation_(true).
    canSeePhysicalLocation_(true).
    canSeePublicAlias_(true).
    canSeePrivateAlias_(true).
    canAddMoreInfo_(true).
    canAddURL_(true).
    canAddImageURL_(true).
    canAddOpenCorporatesUrl_(true).
    canAddCorporateLocation_(true).
    canAddPhysicalLocation_(true).
    canAddPublicAlias_(true).
    canAddPrivateAlias_(true).
    canDeleteCorporateLocation_(true).
    canDeletePhysicalLocation_(true).
    canEditOwnerComment_(true).
    canAddComment_(true).
    canDeleteComment_(true).
    canAddTag_(true).
    canDeleteTag_(true).
    canAddImage_(true).
    canDeleteImage_(true).
    canAddWhereTag_(true).
    canSeeWhereTag_(true).
    canDeleteWhereTag_(true).
    saveMe

}