package code.accountattribute

import code.api.attributedefinition.AttributeDefinitionDI
import code.api.util.APIUtil
import code.setup.ServerSetup
import com.openbankproject.commons.model.enums.{AccountAttributeType, AttributeCategory, AttributeType}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ProductCode, ViewId}
import net.liftweb.common.Full

import scala.concurrent.Await
import scala.concurrent.duration._

class AccountAttributeProviderTest extends ServerSetup {

  Feature("AccountAttributeX provider - CRUD, attribute-name-value filtering, and view visibility") {

    Scenario("create, read, update, delete a single attribute") {
      val bankId = BankId(APIUtil.generateUUID())
      val accountId = AccountId(APIUtil.generateUUID())
      val productCode = ProductCode(APIUtil.generateUUID())

      val created = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
          bankId, accountId, productCode, None, "OVERDRAFT_START_DATE", AccountAttributeType.STRING, "2012-04-23", None), 10.seconds)
      created match {
        case Full(attr) =>
          attr.name should equal("OVERDRAFT_START_DATE")
          attr.value should equal("2012-04-23")

          val fetched = Await.result(
            AccountAttributeX.accountAttributeProvider.vend.getAccountAttributeById(attr.accountAttributeId), 10.seconds)
          fetched.map(_.value) should equal(Full("2012-04-23"))

          val updated = Await.result(
            AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
              bankId, accountId, productCode, Some(attr.accountAttributeId), "OVERDRAFT_START_DATE", AccountAttributeType.STRING, "2013-01-01", None), 10.seconds)
          updated.map(_.value) should equal(Full("2013-01-01"))

          val byAccount = Await.result(
            AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccount(bankId, accountId), 10.seconds)
          byAccount.map(_.map(_.value)) should equal(Full(List("2013-01-01")))

          val deleted = Await.result(
            AccountAttributeX.accountAttributeProvider.vend.deleteAccountAttribute(attr.accountAttributeId), 10.seconds)
          deleted should equal(Full(true))

          val afterDelete = Await.result(
            AccountAttributeX.accountAttributeProvider.vend.getAccountAttributeById(attr.accountAttributeId), 10.seconds)
          afterDelete.isDefined should equal(false)
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getAccountIdsByParams matches any row with a requested name/value pair") {
      val bankId = BankId(APIUtil.generateUUID())
      val accountA = AccountId(APIUtil.generateUUID())
      val accountB = AccountId(APIUtil.generateUUID())
      val accountC = AccountId(APIUtil.generateUUID())
      val productCode = ProductCode(APIUtil.generateUUID())

      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountA, productCode, None, "TIER", AccountAttributeType.STRING, "GOLD", None), 10.seconds)
      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountB, productCode, None, "TIER", AccountAttributeType.STRING, "SILVER", None), 10.seconds)
      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountC, productCode, None, "TIER", AccountAttributeType.STRING, "BRONZE", None), 10.seconds)

      val matched = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.getAccountIdsByParams(
          bankId, Map("TIER" -> List("GOLD"))), 10.seconds)
      matched match {
        case Full(ids) =>
          ids should contain(accountA.value)
          ids should not contain accountB.value
        case other => fail(s"expected Full, got $other")
      }

      val matchedMulti = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.getAccountIdsByParams(
          bankId, Map("TIER" -> List("GOLD", "SILVER"))), 10.seconds)
      matchedMulti match {
        case Full(ids) =>
          ids should contain(accountA.value)
          ids should contain(accountB.value)
          ids should not contain accountC.value
        case other => fail(s"expected Full, got $other")
      }

      val matchedEmpty = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.getAccountIdsByParams(bankId, Map.empty), 10.seconds)
      matchedEmpty match {
        case Full(ids) =>
          ids should contain(accountA.value)
          ids should contain(accountB.value)
          ids should contain(accountC.value)
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getAccountAttributesByAccountCanBeSeenOnView only returns attributes whose definition allows the view") {
      val bankId = BankId(APIUtil.generateUUID())
      val accountId = AccountId(APIUtil.generateUUID())
      val productCode = ProductCode(APIUtil.generateUUID())
      val ownerView = ViewId("owner")
      val otherView = ViewId("_other")

      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        bankId, "VISIBLE_ATTR", AttributeCategory.Account, AttributeType.STRING, "desc", "alias",
        List(ownerView.value), isActive = true), 10.seconds)
      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        bankId, "HIDDEN_ATTR", AttributeCategory.Account, AttributeType.STRING, "desc", "alias",
        List(otherView.value), isActive = true), 10.seconds)

      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountId, productCode, None, "VISIBLE_ATTR", AccountAttributeType.STRING, "v1", None), 10.seconds)
      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountId, productCode, None, "HIDDEN_ATTR", AccountAttributeType.STRING, "v2", None), 10.seconds)

      val visible = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccountCanBeSeenOnView(
          bankId, accountId, ownerView), 10.seconds)
      visible match {
        case Full(attrs) =>
          attrs.map(_.name) should equal(List("VISIBLE_ATTR"))
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getAccountAttributesByAccountsCanBeSeenOnView filters across multiple accounts") {
      val bankId = BankId(APIUtil.generateUUID())
      val accountA = AccountId(APIUtil.generateUUID())
      val accountB = AccountId(APIUtil.generateUUID())
      val productCode = ProductCode(APIUtil.generateUUID())
      val ownerView = ViewId("owner")

      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        bankId, "MULTI_ATTR", AttributeCategory.Account, AttributeType.STRING, "desc", "alias",
        List(ownerView.value), isActive = true), 10.seconds)

      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountA, productCode, None, "MULTI_ATTR", AccountAttributeType.STRING, "a1", None), 10.seconds)
      Await.result(AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(
        bankId, accountB, productCode, None, "MULTI_ATTR", AccountAttributeType.STRING, "b1", None), 10.seconds)

      val result = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccountsCanBeSeenOnView(
          List(BankIdAccountId(bankId, accountA), BankIdAccountId(bankId, accountB)), ownerView), 10.seconds)
      result match {
        case Full(attrs) =>
          attrs.map(_.value).toSet should equal(Set("a1", "b1"))
        case other => fail(s"expected Full, got $other")
      }

      val emptyResult = Await.result(
        AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccountsCanBeSeenOnView(
          Nil, ownerView), 10.seconds)
      emptyResult should equal(Full(Nil))
    }
  }
}
