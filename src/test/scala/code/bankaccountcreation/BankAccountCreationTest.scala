package code.bankaccountcreation

import code.bankconnectors.Connector
import code.model.{AccountId, BankId}
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import net.liftweb.common.Full
import org.scalatest.Tag

class BankAccountCreationTest extends ServerSetup with DefaultUsers with DefaultConnectorTestSetup {

  object BankAccountCreation extends Tag("account_creation")

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

  feature("Bank and bank account creation") {

    val accountNumber = "12313213"
    val accountHolderName = "Rolf Rolfson"
    val accountLabel = accountNumber + " " + accountHolderName
    val accountType = "some-type"
    val currency = "EUR"

//    scenario("Creating a duplicate bank must fail") {
//
//      val bankNationalIdentifier = "bank-identifier"
//      val bankName = "A Bank"
//      Given("A bank that does not exist")
//      Connector.connector.vend.getBanks.size must equal(0)
//
//      val id = "some-bank"
//
//      val newBank1 = createBank(id)
//
//      Then("A bank should now exist, with the correct parameters")
//
//      val allBanks = Connector.connector.vend.getBanks
//      allBanks.size must equal(1)
//
//      val newBank = allBanks(0)
//      newBank.bankId.value must equal(id)
//
//      // TODO Test for duplicate bank must fail. See comments in createBank
//
//    }



    scenario("Creating an account for a bank that does not exist yet") {

      val bankNationalIdentifier = "bank-identifier"
      val bankName = "A Bank"
      Given("A bank that does not exist")
      Connector.connector.vend.getBanks.size must equal(0)

      When("We create an account at that bank")
      val (_, returnedAccount) = Connector.connector.vend.createBankAndAccount(
        bankName, bankNationalIdentifier, accountNumber, accountType,
        accountLabel, currency, accountHolderName,
        "","", "" //added field in V220
      ) 

      Then("A bank should now exist, with the correct parameters")
      val allBanks = Connector.connector.vend.getBanks
      allBanks.size must equal(1)
      val newBank = allBanks(0)
      newBank.fullName must equal(bankName)
      newBank.nationalIdentifier must equal(bankNationalIdentifier)

      And("An account should now exist, with the correct parameters")
      val foundAccountBox = Connector.connector.vend.getBankAccount(newBank.bankId, returnedAccount.accountId)
      foundAccountBox.isDefined must equal(true)
      val foundAccount = foundAccountBox match {
        case Full(f) => f
      }

      foundAccount.number must equal(accountNumber)
      foundAccount.owners must contain(accountHolderName)
    }

    scenario("Creating an account for a bank that already exists") {
      val existingBank = createBank("some-bank")

      Given("A bank that does exist")
      val allBanksBefore = Connector.connector.vend.getBanks
      allBanksBefore.size must equal(1)
      allBanksBefore(0).bankId must equal(existingBank.bankId)


      When("We create an account at that bank")
      val (_, returnedAccount) = Connector.connector.vend.createBankAndAccount(
        existingBank.fullName, 
        existingBank.nationalIdentifier, 
        accountNumber,
        accountType, accountLabel, currency, 
        accountHolderName,
        "","", "" //added field in V220
      )

      Then("No new bank should be created")
      val allBanksAfter = Connector.connector.vend.getBanks
      allBanksAfter.size must equal(1)
      allBanksAfter(0).fullName must equal(existingBank.fullName)
      allBanksAfter(0).nationalIdentifier must equal(existingBank.nationalIdentifier)

      And("An account should now exist, with the correct parameters")
      val foundAccountBox = Connector.connector.vend.getBankAccount(existingBank.bankId, returnedAccount.accountId)
      foundAccountBox.isDefined must equal(true)
      val foundAccount = foundAccountBox match {
        case Full(fa) => fa
      }

      foundAccount.number must equal(accountNumber)
      foundAccount.owners must contain(accountHolderName)
    }

  }

  feature("Bank account creation that fails if the associated bank doesn't exist") {

    val bankId = BankId("some-bank")
    val accountId = AccountId("some-account")
    val currency = "EUR"
    val initialBalance = BigDecimal("1000.00")
    val accountHolderName = "Some Person"
    val defaultAccountNumber = "1231213213"
    val accountType = "some-type"
    val accountLabel = defaultAccountNumber + " " + accountHolderName

    scenario("Creating a bank account when the associated bank does not exist") {
      Given("A bank that doesn't exist")
      Connector.connector.vend.getBank(bankId).isDefined must equal(false)

      When("We try to create an account at that bank")
      Connector.connector.vend.createSandboxBankAccount(
        bankId, accountId, defaultAccountNumber, 
        accountType, accountLabel,
        currency, initialBalance, accountHolderName,
        "","", "" //added field in V220
      ) 

      Then("No account is created")
      Connector.connector.vend.getBankAccount(bankId, accountId).isDefined must equal(false)

    }

    scenario("Creating a bank account with an account number") {
      Given("A bank that does exist")
      createBank(bankId.value)
      Connector.connector.vend.getBank(bankId).isDefined must equal(true)

      When("We try to create an account at that bank")
      Connector.connector.vend.createSandboxBankAccount(bankId, accountId, defaultAccountNumber, accountType, accountLabel, currency, initialBalance, accountHolderName,
                                                        "","","" ) //added field in V220

      Then("An account with the proper parameters should be created")
      val createdAccBox = Connector.connector.vend.getBankAccount(bankId, accountId)
      createdAccBox.isDefined must be(true)
      val createdAcc = createdAccBox match {
        case Full(ca) => ca
      }

      createdAcc.bankId must equal(bankId)
      createdAcc.accountId must equal(accountId)
      createdAcc.balance must equal(initialBalance)
      createdAcc.currency must equal(currency)
      createdAcc.number must equal(defaultAccountNumber)
      createdAcc.accountHolder must equal(accountHolderName)
    }

    scenario("Creating a bank account without an account number") {
      Given("A bank that does exist")
      createBank(bankId.value)
      Connector.connector.vend.getBank(bankId).isDefined must equal(true)

      When("We try to create an account at that bank")
      Connector.connector.vend.createSandboxBankAccount(bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName,
                                                        "","", "")//added field in V220

      Then("An account with the proper parameters should be created")
      val createdAccBox = Connector.connector.vend.getBankAccount(bankId, accountId)
      createdAccBox.isDefined must be(true)
      val createdAcc = createdAccBox match {
        case Full(ca) => ca
      }

      createdAcc.bankId must equal(bankId)
      createdAcc.accountId must equal(accountId)
      createdAcc.balance must equal(initialBalance)
      createdAcc.currency must equal(currency)
      createdAcc.accountHolder must equal(accountHolderName)

      //Account number should be autogenerated
      createdAcc.number.nonEmpty must equal(true)

    }

  }



}
