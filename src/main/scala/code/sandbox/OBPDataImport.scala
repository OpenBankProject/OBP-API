package code.sandbox

import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import code.accountholder.{AccountHolders, MapperAccountHolders}
import code.api.util.APIUtil._
import code.api.util.{APIUtil, ErrorMessages}
import code.crm.CrmEvent.CrmEvent
import code.metadata.counterparties.{Counterparties, MapperCounterparties}
import code.products.Products
import code.products.Products.{Product, ProductCode}
import code.bankconnectors.{Connector, OBPLimit, OBPOffset}
import code.model.dataAccess.ResourceUser
import code.model._
import code.branches.Branches.{Branch, BranchT}
import code.atms.Atms.AtmT
import code.users.Users
import code.util.Helper
import code.views.Views
import net.liftweb.common._
import net.liftweb.mapper.By
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object OBPDataImport extends SimpleInjector {

  val importer =  new Inject(buildOne _) {}

  def buildOne : OBPDataImport = LocalMappedConnectorDataImport

}

trait Saveable[T] {
  val value : T
  def save() : Unit
}

/**
 * This trait attempts to implement as much validation logic as possible, leaving the
 * unimplemented abstract methods for the creation of specific implementations of
 * banks, accounts, transactions, etc.
 *
 * The idea is that the validation happens first, and if everything was okay, everything
 * gets saved. That's the reason for the use of the Saveable trait.
 */
trait OBPDataImport extends MdcLoggable {
  val datePattern = APIUtil.DateWithMs
  val dateFormat = new SimpleDateFormat(datePattern)

  type BankType <: Bank
  type AccountType <: BankAccount
  type MetadataType <: CounterpartyMetadata
  type ViewType <: View
  type TransactionType <: TransactionUUID
  type AccountOwnerUsername = String
  type BranchType <: BranchT
  type AtmType <: AtmT
  type ProductType <: Product
  type CrmEventType <: CrmEvent

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

  /**
   * Create banks that can be saved. This method assumes the banks in @data have passed validation checks and are allowed
   * to be created as is.
   */
  protected def createSaveableBanks(data : List[SandboxBankImport]) : Box[List[Saveable[BankType]]]


  /**
   * Create branches that can be saved.
   */
  protected def createSaveableBranches(data : List[SandboxBranchImport]) : Box[List[Saveable[BranchType]]]

  /**
   * Create atms that can be saved.
   */
  protected def createSaveableAtms(data : List[SandboxAtmImport]) : Box[List[Saveable[AtmType]]]


  /**
   * Create Products that can be saved.
   */
  protected def createSaveableProducts(data : List[SandboxProductImport]) : Box[List[Saveable[ProductType]]]

  /**
   * Create CRM events that can be saved.
   */
  protected def createSaveableCrmEvents(data : List[SandboxCrmEventImport]) : Box[List[Saveable[CrmEventType]]]
  
  
  
  /**
    * Create an firehose view for account with BankId @bankId and AccountId @accountId that can be saved.
    */
  protected def createFirehoseView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType]
  
  /**
   * Create an owner view for account with BankId @bankId and AccountId @accountId that can be saved.
   */
  protected def createOwnerView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType]

  /**
   * Create a public view for account with BankId @bankId and AccountId @accountId that can be saved.
   */
  protected def createPublicView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType]


  /**
   * Create AccountantsView with BankId @bankId and AccountId @accountId that can be saved.
   */
  protected def createAccountantsView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType]


  /**
   * Create AuditorsView with BankId @bankId and AccountId @accountId that can be saved.
   */
  protected def createAuditorsView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType]


  /**
   * Creates an account that can be saved. This method assumes that @acc has passed validatoin checks and is allowed
   * to be created as is.
   */
  protected def createSaveableAccount(acc : SandboxAccountImport, banks : List[BankType]) : Box[Saveable[AccountType]]


  /**
   * Creates an ResourceUser that can be saved. This method assumes there is no existing user with an email
   * equal to @u.email
   */
  protected def createSaveableUser(u : SandboxUserImport) : Box[Saveable[ResourceUser]]

  protected def createUsers(toImport : List[SandboxUserImport]) : Box[List[Saveable[ResourceUser]]] = {
    val existingResourceUsers = toImport.flatMap(u => Users.users.vend.getUserByUserName(u.user_name))
    val allUsernames = toImport.map(_.user_name)
    val duplicateUsernames = allUsernames diff allUsernames.distinct

    def usersExist(existingEmails : List[String]) =
      Failure(s"User(s) with email(s) $existingEmails already exist (and may be different (e.g. different display_name)")

    if(!existingResourceUsers.isEmpty) {
      usersExist(existingResourceUsers.map(_.name))
    } else if(!duplicateUsernames.isEmpty) {
      Failure(s"Users must have unique usernames: Duplicates found: $duplicateUsernames")
    }else {

      val resourceUsers = toImport.map(createSaveableUser(_))

      dataOrFirstFailure(resourceUsers)
    }
  }

  /**
   * Sets the user with email @owner as the owner of @account
   *
   * TODO: this only works after createdUsers have been saved (and thus an ResourceUser has been created
   */
  protected def setAccountOwner(owner : AccountOwnerUsername, account: BankAccount, createdUsers: List[ResourceUser]): AnyVal = {
    val resourceUserOwner = createdUsers.find(user => owner == user.name)
    //println("{resourceUserOwner: " + resourceUserOwner)

    resourceUserOwner match {
      case Some(user) => {
        val accountHolder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user, BankIdAccountId(account.bankId, account.accountId))
      }
      case None => {
        //This shouldn't happen as AuthUser should generate the ResourceUsers when saved
        logger.error(s"api user $owner not found.")
        logger.error("Data import completed with errors.")
      }
    }
  }

  /**
   * Creates a saveable transaction object. This method assumes the transaction has passed
   * preliminary validation checks.
   */
  protected def createSaveableTransaction(t : SandboxTransactionImport, createdBanks : List[BankType], createdAccounts : List[AccountType]) :
  Box[Saveable[TransactionType]]


  final protected def createBanks(data : SandboxDataImport) = {
    val existing = data.banks.flatMap(b => Connector.connector.vend.getBank(BankId(b.id), None).map(_._1))

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


  final protected def createDataLicences(data : SandboxDataImport) = {

    throw new Exception (ErrorMessages.NotImplemented)

  }


  final protected def createBranches(data : SandboxDataImport) = {


    logger.info("Hello from createBranches")



    // TODO Check the data.branches is OK before calling the following

    createSaveableBranches(data.branches)


    /*
    val existing = data.licenses.flatMap(lic => Connector.connector.vend.getDataLicense(BankId(lic.id)))

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
      createSaveableDataLicenses(data.licenses)
    }
    */
  }


  final protected def createAtms(data : SandboxDataImport) = {

    logger.info("Hello from createAtms")
    // TODO Check the data.atms is OK before calling the following

    createSaveableAtms(data.atms)
  }


  final protected def createProducts(data : SandboxDataImport) = {

    logger.info("Hello from createProducts")
    // Check the data products is OK before calling calling createSaveableProducts

    logger.debug("Get existing products that match the bank id and product code")
    val existing = data.products.flatMap(p => Products.productsProvider.vend.getProduct(BankId(p.bank_id), ProductCode(p.code), true))

    val allNewCodes = data.products.map(_.code)
    val emptyCodes = allNewCodes.filter(_.isEmpty)
    val uniqueNewCodes = data.products.map(_.code).distinct
    val duplicateCodes = allNewCodes diff uniqueNewCodes

    if(!existing.isEmpty) {
      val existingCodes = existing.map(_.code.value)
      Failure(s"Existing Product codes were found for the bank $existingCodes")
    } else if (!emptyCodes.isEmpty){
      Failure(s"Product(s) with empty codes are not allowed")
    } else if(!duplicateCodes.isEmpty) {

      val duplicateProducts = duplicateCodes.flatMap(d => data.products.filter(_.code == d))

      duplicateProducts.foreach (dc => logger.error (s"Duplicate products found (duplicate code) in data.products Code: ${dc.code} Name: ${dc.name} Category: ${dc.category}"))
      Failure(s"Products must have unique codes. Duplicates found: $duplicateCodes")
    } else {
      createSaveableProducts(data.products)
    }
  }


  final protected def createCrmEvents(data : SandboxDataImport) = {
      createSaveableCrmEvents(data.crm_events)
  }







  final protected def validateAccount(acc : SandboxAccountImport, data : SandboxDataImport) : Box[SandboxAccountImport] = {
    for {
      ownersNonEmpty <- Helper.booleanToBox(acc.owners.nonEmpty) ?~
        s"Accounts must have at least one owner. Violation: (bank id ${acc.bank}, account id ${acc.id})"
      ownersDefinedInDataImport <- Helper.booleanToBox(acc.owners.forall(ownerUsername => data.users.exists(u => u.user_name == ownerUsername))) ?~ {
        val violations = acc.owners.filter(ownerUsername => !data.users.exists(u => u.user_name == ownerUsername))
        s"Accounts must have owner(s) defined in data import. Violation: ${violations.mkString(",")}"
      }
      accId = AccountId(acc.id)
      bankId = BankId(acc.bank)
      //TODO Check the following logic which breaks sandbox tests after ViewsImpl refactoring
      //ownerViewDoesNotExist <- Helper.booleanToBox(Views.views.vend.view(ViewUID(ViewId("owner"), bankId, accId)).isEmpty) ?~ {
      //  s"owner view for account ${acc.id} at bank ${acc.bank} already exists"
      //}
      //publicViewDoesNotExist <- Helper.booleanToBox(Views.views.vend.view(ViewUID(ViewId("public"), bankId, accId)).isEmpty) ?~ {
      //  s"public view for account ${acc.id} at bank ${acc.bank} already exists"
      //}
    } yield acc
  }

  final protected def createAccountsAndViews(data : SandboxDataImport, banks : List[BankType]) : Box[List[(Saveable[AccountType], List[ViewType], List[AccountOwnerUsername])]] = {

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

      val validatedAccounts = dataOrFirstFailure(data.accounts.map(validateAccount(_, data)))

      validatedAccounts.flatMap(createSaveableAccountResults(_, banks))
    }
  }

  final protected def createSaveableAccountResults(accs : List[SandboxAccountImport], banks : List[BankType])
  : Box[List[(Saveable[AccountType], List[ViewType], List[AccountOwnerUsername])]] = {

    logger.info("Hello from createSaveableAccountResults")

    val saveableAccounts =
      for(acc <- accs)
        yield for {
          saveableAccount <- createSaveableAccount(acc, banks)
        } yield {
          (saveableAccount, createViews(acc), acc.owners)
        }

    dataOrFirstFailure(saveableAccounts)
  }

  /**
   * Creates the owner view and a public view (if the public view is requested), for an account.
   */
  final protected def createViews(acc : SandboxAccountImport) : List[ViewType] = {
    val bankId = BankId(acc.bank)
    val accountId = AccountId(acc.id)
  
    val firehoseView =
      // Only create Firehose view if they are enabled at instance.
      if (APIUtil.getPropsAsBoolValue("allow_firehose_views", false))
        createFirehoseView(bankId, accountId, "Firehose View")
      else Empty
    
    val ownerView =
        createOwnerView(bankId, accountId, "Owner View")

    val publicView =
      if(acc.generate_public_view)
        createPublicView(bankId, accountId, "Public View")
      else Empty

    val accountantsView =
      if(acc.generate_accountants_view)
        createAccountantsView(bankId, accountId, "Accountants View")
      else Empty

    val auditorsView =
      if(acc.generate_auditors_view)
        createAuditorsView(bankId, accountId, "Auditors View")
      else Empty

    List(firehoseView, ownerView, publicView, accountantsView, auditorsView).flatten
  }

  final protected def createTransactions(data : SandboxDataImport, createdBanks : List[BankType], createdAccounts : List[AccountType]) : Box[List[Saveable[TransactionType]]] = {

    def accountSpecifiedInImport(t : SandboxTransactionImport) : Boolean = {
      data.accounts.exists(acc => acc.bank == t.this_account.bank && acc.id == t.this_account.id)
    }

    val transactionsWithNoAccountSpecifiedInImport = data.transactions.filterNot(accountSpecifiedInImport)
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

      /**
       * Because we want to generate placeholder counterparty names if they're not present, but also want to have counterparties with
       * missing names but the same account number share metadata, we need to keep track of all generated names and the account numbers
       * to which they are linked to avoid generating two names for the same account number
       */
      val emptyHoldersAccNums = scala.collection.mutable.Map[String, String]()

      def randomCounterpartyHolderName(accNumber: Option[String]) : String = {
        val name = s"unknown_${UUID.randomUUID.toString}"
        accNumber.foreach(emptyHoldersAccNums.put(_, name))
        logger.debug(s"randomCounterpartyHolderName will return $name")
        name
      }

      //TODO validate numbers and dates in one place
      val results = data.transactions.map(t => {


        val counterpartyAccNumber = t.counterparty.flatMap(_.account_number)

        //If the counterparty name is present in 't', then use it
        val counterpartyHolder = t.counterparty.flatMap(_.name) match {
          case Some(holder) if holder.nonEmpty => holder
          case _ => {
            counterpartyAccNumber match {
              case Some(accNum) if accNum.nonEmpty => {
                val existing = emptyHoldersAccNums.get(accNum)
                existing match {
                  case Some(existingValue) => {
                    logger.debug (s"counterpartyHolder will be $existingValue")
                    existingValue
                  } //holder already generated for an empty-name counterparty with the same account number
                  case None => randomCounterpartyHolderName(Some(accNum)) //generate a new counterparty name
                }
              }
              //no name, no account number, generate a random new holder
              case _ => randomCounterpartyHolderName(None)
            }
          }
        }

        //fill in the "correct" counterparty name
        val modifiedTransaction  = t.copy(counterparty = Some(SandboxTransactionCounterparty(name = Some(counterpartyHolder), account_number = counterpartyAccNumber)))

        createSaveableTransaction(modifiedTransaction, createdBanks, createdAccounts)
      })

      dataOrFirstFailure(results)
    }
  }

  /**
   * @param data
   * @return A full box if the import worked, or else a failure describing what went wrong
   */
  def importData(data: SandboxDataImport) : Box[Unit] = {
    logger.info(s"Hello from importData")
    for {
      banks <- createBanks(data)
      users <- createUsers(data.users)
      accountResults <- createAccountsAndViews(data, banks.map(_.value))
      transactions <- createTransactions(data, banks.map(_.value), accountResults.map(_._1.value))
      branches <- createBranches(data)
      atms <- createAtms(data)
      products <- createProducts(data)
      crmEvents <- createCrmEvents(data)
    } yield {
      logger.info(s"importData is saving ${banks.size} banks..")
      banks.foreach(_.save())

      logger.info(s"importData is saving ${users.size} users..")
      users.foreach(_.save())

      logger.info(s"importData is saving ${branches.size} branches..")
      branches.foreach(_.save())

      logger.info(s"importData is saving ${atms.size} ATMs..")
      atms.foreach(_.save())

      logger.info(s"importData is saving ${products.size} products..")
      products.foreach(_.save())

      logger.info(s"importData is saving ${crmEvents.size} crmEvents..")
      crmEvents.foreach(_.save())



      val us = Users.users.vend.getAllUsers() match {
        case Full(userList) => userList
        case _ => List()
      }
      logger.info(s"importData is saving ${accountResults.size} accountResults (accounts, views and permissions)..")
      accountResults.foreach {
        case (account, views, accOwnerUsernames) =>
          account.save()

          views.filterNot(_.isPublic).foreach(v => {
            //grant the owner access to Private views
            //this should always find the owners as that gets verified at an earlier stage, but it's not perfect this way
            val accOwners = us.filter(u => accOwnerUsernames.exists(name => u.name == name))
            accOwners.foreach(Views.views.vend.addPermission(v.uid, _))
          })

          accOwnerUsernames.foreach(setAccountOwner(_, account.value, us))
      }
      logger.info(s"importData is saving ${transactions.size} transactions (and loading them again)")
      transactions.foreach { t =>
        t.save()
        //load it to force creation of metadata (If we are using Mapped connector, MappedCounterpartyMetadata.create will be called)
        val lt = Connector.connector.vend.getTransaction(t.value.theBankId, t.value.theAccountId, t.value.theTransactionId)
      }
    }
  }


  logger.info("Done")
}


case class SandboxBankImport(
  id : String,
  short_name : String,
  full_name : String,
  logo : String,
  website : String)


// Branches to be imported must match this pattern
case class SandboxBranchImport(
  id : String,
  bank_id: String,
  name : String,
  address : SandboxAddressImport,
  location : SandboxLocationImport,
  meta : SandboxMetaImport,
  lobby : Option[SandboxLobbyImport],
  driveUp : Option[SandboxDriveUpImport])

case class SandboxLicenseImport(
   id : String,
   name : String)

case class SandboxMetaImport(
   license : SandboxLicenseImport)

case class SandboxLobbyImport(
  hours : String)

case class SandboxDriveUpImport(
  hours : String)

case class SandboxAddressImport(
   line_1 : String,
   line_2 : String,
   line_3 : String,
   city : String,
   county : String, // Division of State
   state : String, // Division of Country
   post_code : String,
   country_code: String)

case class SandboxLocationImport(
  latitude : Double,
  longitude : Double)

case class SandboxUserImport(
  email : String,
  password : String,
  user_name : String)

case class SandboxAccountImport(
  id : String,
  bank : String,
  label : String,
  number : String,
  `type` : String,
  balance : SandboxBalanceImport,
  IBAN : String,
  owners : List[String],
  generate_public_view : Boolean,
  generate_accountants_view : Boolean,
  generate_auditors_view : Boolean)

case class SandboxBalanceImport(
  currency : String,
  amount : String)

case class SandboxTransactionImport(
  id : String,
  this_account : SandboxAccountIdImport,
  counterparty : Option[SandboxTransactionCounterparty],
  details : SandboxAccountDetailsImport)

case class SandboxTransactionCounterparty(
  name : Option[String],  // Also known as Label
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


case class SandboxAtmImport(
   id : String,
   bank_id: String,
   name : String,
   address : SandboxAddressImport,
   location : SandboxLocationImport,
   meta : SandboxMetaImport
   )


case class SandboxProductImport(
   bank_id : String,
   code: String,
   name : String,
   category : String,
   family : String,
   super_family : String,
   more_info_url : String,
   meta : SandboxMetaImport
   )


case class SandboxDataImport(
  banks : List[SandboxBankImport],
  users : List[SandboxUserImport],
  accounts : List[SandboxAccountImport],
  transactions : List[SandboxTransactionImport],
  branches: List[SandboxBranchImport],
  atms: List[SandboxAtmImport],
  products: List[SandboxProductImport],
  crm_events: List[SandboxCrmEventImport]
)


case class SandboxCrmEventImport (
   id : String, // crmEventId
   bank_id : String,
   customer: SandboxCustomerImport,
   category : String,
   detail : String,
   channel : String,
   actual_date: String
   )

case class SandboxCustomerImport (
   name: String,
   number : String // customer number, also known as ownerId (owner of accounts) aka API User?
   )



object SandboxData{
  val bank1 = SandboxBankImport(id = "bank1", short_name = "bank 1", full_name = "Bank 1 Inc.",
    logo = "http://example.com/logo", website = "http://example.com")
  val bank2 = SandboxBankImport(id = "bank2", short_name = "bank 2", full_name = "Bank 2 Inc.",
    logo = "http://example.com/logo2", website = "http://example.com/2")

  val standardAddress1 = SandboxAddressImport(line_1 = "5 Some Street", line_2 = "Rosy Place", line_3 = "Sunny Village",
    city = "Ashbourne", county = "Derbyshire",  state = "", post_code = "WHY RU4", country_code = "UK")

  val standardLocation1 = SandboxLocationImport(52.556198, 13.384099)


  val standardLicense = SandboxLicenseImport  (id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")
  val standardMeta = SandboxMetaImport (license = standardLicense)

  val standardLobby = SandboxLobbyImport(hours = "M-TH 8:30-3:30, F 9-5")
  val standardDriveUp = SandboxDriveUpImport(hours = "M-Th 8:30-5:30, F-8:30-6, Sat 9-12")

  val branch1AtBank1 = SandboxBranchImport(id = "branch1", name = "Genel Müdürlük", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta, lobby = Option(standardLobby), driveUp = Option(standardDriveUp))
  val branch2AtBank1 = SandboxBranchImport(id = "branch2", name = "Manchester", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta, lobby = Option(standardLobby), driveUp = Option(standardDriveUp))

  val atm1AtBank1 = SandboxAtmImport(id = "atm1", name = "Ashbourne Atm 1", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta)
  val atm2AtBank1 = SandboxAtmImport(id = "atm2", name = "Manchester Atm 1", bank_id = "bank1", address = standardAddress1
    , location = standardLocation1, meta = standardMeta)

  val product1AtBank1 = SandboxProductImport(
    bank_id = "bank1",
    code = "prd1",
    name = "product 1",
    category = "cat1",
    family = "fam1",
    super_family = "sup fam 1",
    more_info_url = "www.example.com/index1",
    meta = standardMeta
  )

  val product2AtBank1 = SandboxProductImport(
    bank_id = "bank1",
    code = "prd2",
    name = "Product 2",
    category = "cat2",
    family = "fam2",
    super_family = "sup fam 2",
    more_info_url = "www.example.com/index2",
    meta = standardMeta
  )

  val user1 = SandboxUserImport(email = "user1@example.com", password = "TESOBE520berlin123!", user_name = "User 1")
  val user2 = SandboxUserImport(email = "user2@example.com", password = "TESOBE520berlin123!", user_name = "User 2")

  val sandboxBalanceImport = SandboxBalanceImport(currency = "EUR", amount = "1000.00")
  
  val account1AtBank1 = SandboxAccountImport(id = "account1", bank = "bank1", label = "Account 1 at Bank 1",
    number = "1", `type` = "savings", IBAN = "1234567890", generate_public_view = true, owners = List(user1.user_name),
    balance = sandboxBalanceImport, generate_accountants_view = true, generate_auditors_view = true)

  val account2AtBank1 = SandboxAccountImport(id = "account2", bank = "bank1", label = "Account 2 at Bank 1",
    number = "2", `type` = "current", IBAN = "91234567890", generate_public_view = false, owners = List(user2.user_name),
    balance = sandboxBalanceImport, generate_accountants_view = true, generate_auditors_view = true)

  val account1AtBank2 = SandboxAccountImport(id = "account1", bank = "bank2", label = "Account 1 at Bank 2",
    number = "22", `type` = "savings", IBAN = "21234567890", generate_public_view = false, owners = List(user1.user_name, user2.user_name),
    balance = sandboxBalanceImport, generate_accountants_view = true, generate_auditors_view = true)

  val counterparty1 = SandboxTransactionCounterparty(name = Some("Acme Inc."), account_number = Some("12345-B"))
  
  val sandboxAccountDetailsImport = SandboxAccountDetailsImport(
    `type` = "SEPA",
    description = "some description",
    posted = "2012-03-07T00:00:00.001Z",
    completed = "2012-04-07T00:00:00.001Z",
    new_balance = "1244.00",
    value = "-135.33"
  )

  val sandboxAccountIdImport = SandboxAccountIdImport(id = account1AtBank1.id, bank=account1AtBank1.bank)
  
  val transactionWithCounterparty = SandboxTransactionImport(id = "transaction-with-counterparty",
    this_account = sandboxAccountIdImport,
    counterparty = Some(counterparty1),
    details = sandboxAccountDetailsImport
  )

  
  val transactionWithoutCounterparty = SandboxTransactionImport(id = "transaction-without-counterparty",
    this_account = sandboxAccountIdImport,
    counterparty = None,
    details = sandboxAccountDetailsImport
  )

  val standardCustomer1 = SandboxCustomerImport("James Brown", "698761728934")

  val standardCrmEvent1 = SandboxCrmEventImport("ASDFHJ47YKJH", bank1.id, standardCustomer1, "Call", "Check mortgage", "Phone", DateWithMsExampleString)
  val standardCrmEvent2 = SandboxCrmEventImport("KIFJA76876AS", bank1.id, standardCustomer1, "Call", "Check mortgage", "Phone", DateWithMsExampleString)

    //same transaction id as another one used, but for a different bank account, so it should work
    val anotherTransaction = SandboxTransactionImport(id = transactionWithoutCounterparty.id,
      this_account = SandboxAccountIdImport(id = account1AtBank2.id, bank=account1AtBank2.bank),
      counterparty = None,
      details = sandboxAccountDetailsImport
    )

    val blankCounterpartyNameTransaction  = SandboxTransactionImport(id = "blankCounterpartNameTransaction",
      this_account = sandboxAccountIdImport,
      counterparty = Some(counterparty1.copy(name = None, account_number=Some("123456-AVB"))),
      details = sandboxAccountDetailsImport
    )

    val blankCounterpartyAccountNumberTransaction  = SandboxTransactionImport(id = "blankCounterpartAccountNumberTransaction",
      this_account = sandboxAccountIdImport,
      counterparty = Some(counterparty1.copy(name=Some("Piano Repair"), account_number= None)),
      details = sandboxAccountDetailsImport
    )

    val importJson = SandboxDataImport(
      bank1 :: bank2 :: Nil, 
      user1 :: user2 :: Nil, 
      account1AtBank1 :: account2AtBank1 :: account1AtBank2 :: Nil, 
      anotherTransaction :: blankCounterpartyNameTransaction :: blankCounterpartyAccountNumberTransaction :: transactionWithCounterparty :: transactionWithoutCounterparty :: Nil, 
      branch1AtBank1 :: branch2AtBank1 :: Nil, 
      atm1AtBank1 :: atm2AtBank1 :: Nil, 
      product1AtBank1 :: product2AtBank1 :: Nil, 
      standardCrmEvent1 :: standardCrmEvent2 :: Nil
    )

    val allFields =
      for (
        v <- this.getClass.getDeclaredFields
        //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
        if (APIUtil.notExstingBaseClass(v.getName()))
      )
        yield {
          v.setAccessible(true)
          v.get(this)
        }
  }