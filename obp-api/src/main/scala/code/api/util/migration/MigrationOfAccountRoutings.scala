package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.bankconnectors.DoobieBankAccountRoutingQueries
import com.openbankproject.commons.model.{AccountId, BankId}

/**
 * Historical migration whose populate() only records that BankAccountRouting replaced
 * MappedBankAccount.accountIban - already applied everywhere. createBankAccountRouting below is
 * not called by populate() or from anywhere else; kept as it was (unreachable) rather than
 * deleted, now rewritten against DoobieBankAccountRoutingQueries instead of the deleted Lift
 * BankAccountRouting entity. tableExists(BankAccountRouting) becomes tableExistsByName, since
 * the table is now created by Liquibase (the table is in db/changelog/db.changelog-baseline.yaml).
 */
object MigrationOfAccountRoutings {

  private val tableName = "bankaccountrouting"

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def populate(name: String): Boolean = {
    DbFunction.tableExistsByName(tableName) match {
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
    val bId = BankId(bankId)
    val aId = AccountId(accountId)
    // query according unique index: UniqueIndex(BankId, AccountId, AccountRoutingScheme)
    DoobieBankAccountRoutingQueries.findByBankAccountScheme(bId, aId, accountRoutingScheme) match {
      case Some(routing) if routing.accountRouting.address == accountRoutingAddress =>
        false // DB have the same routing
      case Some(_) =>
        // only accountRoutingAddress is different.
        DoobieBankAccountRoutingQueries.updateAddress(bId, aId, accountRoutingScheme, accountRoutingAddress) > 0
      case None =>
        // query according unique index: UniqueIndex(BankId, AccountRoutingScheme, AccountRoutingAddress)
        DoobieBankAccountRoutingQueries.findByBankSchemeAddress(bId, accountRoutingScheme, accountRoutingAddress) match {
          case Some(_) =>
            // only accountId is different
            DoobieBankAccountRoutingQueries.updateAccountId(bId, accountRoutingScheme, accountRoutingAddress, aId) > 0
          case None =>
            // not exists corresponding routing in DB.
            DoobieBankAccountRoutingQueries.create(bId, aId, accountRoutingScheme, accountRoutingAddress)
            true
        }
    }
  }
}
