package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import net.liftweb.common.Full
import net.liftweb.mapper.{By, DB, NotNullRef}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfAccountRoutings {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def populate(name: String): Boolean = {
    DbFunction.tableExists(BankAccountRouting) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit

        val isSuccessful = true
        val endDate = System.currentTimeMillis()
        val comment: String =
          s""""Use BankAccountRouting model to store IBAN and other account routings
             |The field MappedBankAccount.accountIban has been removed""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""BankAccountRouting table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  /**
   * create BankAccountRouting if not exists
   * @param bankId
   * @param accountId
   * @param accountRoutingScheme
   * @param accountRoutingAddress
   */
  private def createBankAccountRouting(bankId: String, accountId: String, accountRoutingScheme: String, accountRoutingAddress: String): Boolean = {
    // query according unique index: UniqueIndex(BankId, AccountId, AccountRoutingScheme)
    BankAccountRouting.find(By(BankAccountRouting.BankId, bankId),
      By(BankAccountRouting.AccountId, accountId),
      By(BankAccountRouting.AccountRoutingScheme, accountRoutingScheme)
    ) match {
      case Full(routing) if routing.accountRouting.address == accountRoutingAddress =>
        false // DB have the same routing
      case Full(routing) =>
        // only accountRoutingAddress is different.
        routing.AccountRoutingAddress(accountRoutingAddress).save
      case _ =>
        // query according unique index: UniqueIndex(BankId, AccountRoutingScheme, AccountRoutingAddress)
        BankAccountRouting.find(By(BankAccountRouting.BankId, bankId),
            By(BankAccountRouting.AccountRoutingScheme, accountRoutingScheme),
            By(BankAccountRouting.AccountRoutingAddress, accountRoutingAddress),
          ) match {
          case Full(routing) =>
            // only accountId is different
            routing.AccountId(accountId).save
          case _ =>
            // not exists corresponding routing in DB.
            BankAccountRouting.create
              .BankId(bankId)
              .AccountId(accountId)
              .AccountRoutingScheme(accountRoutingScheme)
              .AccountRoutingAddress(accountRoutingAddress)
              .save
        }
    }
  }
}
