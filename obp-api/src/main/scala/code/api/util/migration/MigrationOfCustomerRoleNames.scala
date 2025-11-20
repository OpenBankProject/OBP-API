package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.entitlement.MappedEntitlement
import code.scope.MappedScope
import net.liftweb.mapper.By
import net.liftweb.common.{Box, Empty, Full}

object MigrationOfCustomerRoleNames {

  // Define role mappings: old role name -> new role name
  private val roleMappings = Map(
    "CanGetCustomer" -> "CanGetCustomersAtOneBank",
    "CanGetCustomers" -> "CanGetCustomersAtOneBank",
    "CanGetCustomersAtAnyBank" -> "CanGetCustomersAtAllBanks",
    "CanGetCustomersMinimal" -> "CanGetCustomersMinimalAtOneBank",
    "CanGetCustomersMinimalAtAnyBank" -> "CanGetCustomersMinimalAtAllBanks"
  )

  def renameCustomerRoles(name: String): Boolean = {
    DbFunction.tableExists(MappedEntitlement) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        try {
          // Make back up of entitlement and scope tables
          DbFunction.makeBackUpOfTable(MappedEntitlement)
          if (DbFunction.tableExists(MappedScope)) {
            DbFunction.makeBackUpOfTable(MappedScope)
          }

          var totalEntitlementsUpdated = 0
          var totalEntitlementsDeleted = 0
          var totalScopesUpdated = 0
          var totalScopesDeleted = 0
          val detailedLog = new StringBuilder()

          // Process each role mapping
          roleMappings.foreach { case (oldRoleName, newRoleName) =>
            detailedLog.append(s"\n--- Processing: $oldRoleName -> $newRoleName ---\n")

            // Process Entitlements
            val oldEntitlements = MappedEntitlement.findAll(By(MappedEntitlement.mRoleName, oldRoleName))
            detailedLog.append(s"Found ${oldEntitlements.size} entitlements with role '$oldRoleName'\n")

            oldEntitlements.foreach { oldEntitlement =>
              val bankId = oldEntitlement.bankId
              val userId = oldEntitlement.userId
              val createdByProcess = oldEntitlement.createdByProcess

              // Check if an entitlement with the new role name already exists for this user/bank combination
              val existingNewEntitlement = MappedEntitlement.find(
                By(MappedEntitlement.mBankId, bankId),
                By(MappedEntitlement.mUserId, userId),
                By(MappedEntitlement.mRoleName, newRoleName)
              )

              existingNewEntitlement match {
                case Full(_) =>
                  // New role already exists, delete the old one to avoid duplicates
                  detailedLog.append(s"  Entitlement already exists for user=$userId, bank=$bankId, role=$newRoleName - deleting old entitlement\n")
                  MappedEntitlement.delete_!(oldEntitlement)
                  totalEntitlementsDeleted += 1

                case Empty | _ =>
                  // New role doesn't exist, rename the old one
                  detailedLog.append(s"  Renaming entitlement for user=$userId, bank=$bankId: $oldRoleName -> $newRoleName\n")
                  oldEntitlement.mRoleName(newRoleName).saveMe()
                  totalEntitlementsUpdated += 1
              }
            }

            // Process Scopes (if table exists)
            if (DbFunction.tableExists(MappedScope)) {
              val oldScopes = MappedScope.findAll(By(MappedScope.mRoleName, oldRoleName))
              detailedLog.append(s"Found ${oldScopes.size} scopes with role '$oldRoleName'\n")

              oldScopes.foreach { oldScope =>
                val bankId = oldScope.bankId
                val consumerId = oldScope.consumerId

                // Check if a scope with the new role name already exists for this consumer/bank combination
                val existingNewScope = MappedScope.find(
                  By(MappedScope.mBankId, bankId),
                  By(MappedScope.mConsumerId, consumerId),
                  By(MappedScope.mRoleName, newRoleName)
                )

                existingNewScope match {
                  case Full(_) =>
                    // New role already exists, delete the old one to avoid duplicates
                    detailedLog.append(s"  Scope already exists for consumer=$consumerId, bank=$bankId, role=$newRoleName - deleting old scope\n")
                    MappedScope.delete_!(oldScope)
                    totalScopesDeleted += 1

                  case Empty | _ =>
                    // New role doesn't exist, rename the old one
                    detailedLog.append(s"  Renaming scope for consumer=$consumerId, bank=$bankId: $oldRoleName -> $newRoleName\n")
                    oldScope.mRoleName(newRoleName).saveMe()
                    totalScopesUpdated += 1
                }
              }
            }
          }

          val endDate = System.currentTimeMillis()
          val comment: String =
            s"""Customer Role Names Migration Completed Successfully
               |
               |Role Mappings Applied:
               |${roleMappings.map { case (old, newRole) => s"  $old -> $newRole" }.mkString("\n")}
               |
               |Summary:
               |  Entitlements Updated: $totalEntitlementsUpdated
               |  Entitlements Deleted (duplicates): $totalEntitlementsDeleted
               |  Scopes Updated: $totalScopesUpdated
               |  Scopes Deleted (duplicates): $totalScopesDeleted
               |
               |Detailed Log:
               |${detailedLog.toString()}
               |""".stripMargin

          isSuccessful = true
          saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
          isSuccessful

        } catch {
          case e: Exception =>
            val endDate = System.currentTimeMillis()
            val comment: String =
              s"""Migration failed with exception: ${e.getMessage}
                 |Stack trace: ${e.getStackTrace.mkString("\n")}
                 |""".stripMargin
            saveLog(name, commitId, isSuccessful = false, startDate, endDate, comment)
            false
        }

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${MappedEntitlement._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}