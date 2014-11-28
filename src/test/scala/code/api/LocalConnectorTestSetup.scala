package code.api

import java.util.Date

import bootstrap.liftweb.ToSchemify
import code.bankconnectors.{OBPLimit, OBPOffset, Connector}
import code.model._
import code.model.dataAccess._
import com.mongodb.QueryBuilder
import net.liftweb.mapper.MetaMapper
import net.liftweb.mongodb._
import net.liftweb.util.Helpers._

import scala.math.BigDecimal
import scala.math.BigDecimal.RoundingMode
import scala.util.Random._

trait LocalConnectorTestSetup extends TestConnectorSetup {

  override protected def createBank(id : String) : Bank = {
    HostedBank.createRecord.
      name(randomString(5)).
      alias(randomString(5)).
      permalink(id).
      national_identifier(randomString(5)).
      save
  }

  override protected def createAccountAndOwnerView(accountOwner: Option[User], bankId: BankId, accountId : AccountId, currency : String) : Account = {

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


  override protected def createTransaction(account: BankAccount, startDate: Date, finishDate: Date) = {

    val thisAccountBank = OBPBank.createRecord.
      IBAN(randomString(5)).
      national_identifier(account.nationalIdentifier).
      name(account.bankName)
    val thisAccount = OBPAccount.createRecord.
      holder(account.accountHolder).
      number(account.number).
      kind(account.accountType).
      bank(thisAccountBank)

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
      currency(account.currency).
      amount(account.balance + transactionAmount)

    val newValue : OBPValue = OBPValue.createRecord.
      currency(account.currency).
      amount(transactionAmount)

    val details ={
      OBPDetails
        .createRecord
        .kind(randomString(5))
        .posted(startDate)
        .other_data(randomString(5))
        .new_balance(newBalance)
        .value(newValue)
        .completed(finishDate)
        .label(randomString(5))
    }
    val transaction = OBPTransaction.createRecord.
      this_account(thisAccount).
      other_account(otherAccount).
      details(details)

    val env = OBPEnvelope.createRecord.
      obp_transaction(transaction).save

    //slightly ugly
    account.asInstanceOf[Account].accountBalance(newBalance.amount.get).lastUpdate(now).save

    env.save
  }

  private def ownerViewImpl(bankId : BankId, accountId : AccountId) : ViewImpl =
    ViewImpl.createAndSaveOwnerView(bankId, accountId, randomString(3))

  override protected def createOwnerView(bankId: BankId, accountId: AccountId) : View = {
    ownerViewImpl(bankId, accountId)
  }

  override protected def createPublicView(bankId: BankId, accountId: AccountId) : View = {
    ViewImpl.createAndSaveDefaultPublicView(bankId, accountId, randomString(3))
  }

  override protected def createRandomView(bankId: BankId, accountId: AccountId) : View = {
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

  override protected def setAccountHolder(user: User, bankId : BankId, accountId : AccountId) = {
    MappedAccountHolder.create.
      user(user.apiId.value).
      accountBankPermalink(bankId.value).
      accountPermalink(accountId.value).save
  }

  override protected def grantAccessToAllExistingViews(user : User) = {
    ViewImpl.findAll.foreach(v => {
      ViewPrivileges.create.
        view(v).
        user(user.apiId.value).
        save
    })
  }

  override protected def wipeTestData() = {
    //drop the mongo Database after each test
    MongoDB.getDb(DefaultMongoIdentifier).foreach(_.dropDatabase())

    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == OBPUser || m == APIUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }

}
