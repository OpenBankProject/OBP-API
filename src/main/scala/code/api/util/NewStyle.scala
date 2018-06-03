package code.api.util

import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import com.github.dwickern.macros.NameOf.nameOf

object NewStyle {
  lazy val endpoints: List[(String, String)] = List(
    (nameOf(Implementations3_0_0.getUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getCurrentUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUserByUserId), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUserByUsername), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUsers), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUsers), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations3_0_0.getCustomersForUser), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations3_0_0.getCustomersForUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getCoreTransactionsForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getTransactionsForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.corePrivateAccountsAllBanks), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getViewsForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getPrivateAccountIdsbyBankId), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.privateAccountsAtOneBank), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getCoreAccountById), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getPrivateAccountById), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAtm), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAtms), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getBranch), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getBranches), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.addEntitlementRequest), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAllEntitlementRequests), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getEntitlementRequests), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getEntitlementRequestsForCurrentUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getEntitlementsForCurrentUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.deleteEntitlementRequest), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.createViewForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.updateViewForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.dataWarehouseSearch), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.addScope), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.deleteScope), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getScopes), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.dataWarehouseStatistics), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getBanks), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.bankById), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getPermissionForUserForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations2_2_0.config), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_0_0.getAllEntitlements), ApiVersion.v2_0_0.toString)
  )
}
