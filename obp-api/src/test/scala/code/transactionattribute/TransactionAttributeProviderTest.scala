package code.transactionattribute

import code.api.attributedefinition.AttributeDefinitionDI
import code.api.util.APIUtil
import code.setup.ServerSetup
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType, TransactionAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionId, ViewId}
import net.liftweb.common.Full

import scala.concurrent.Await
import scala.concurrent.duration._

class TransactionAttributeProviderTest extends ServerSetup {

  Feature("TransactionAttributeX provider - CRUD, attribute-name-value filtering, and view visibility") {

    Scenario("create, read, update, delete a single attribute") {
      val bankId = BankId(APIUtil.generateUUID())
      val transactionId = TransactionId(APIUtil.generateUUID())

      val created = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
          bankId, transactionId, None, "INVOICE_NUMBER", TransactionAttributeType.STRING, "INV-001"), 10.seconds)
      created match {
        case Full(attr) =>
          attr.name should equal("INVOICE_NUMBER")
          attr.value should equal("INV-001")

          val fetched = Await.result(
            TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributeById(attr.transactionAttributeId), 10.seconds)
          fetched.map(_.value) should equal(Full("INV-001"))

          val updated = Await.result(
            TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
              bankId, transactionId, Some(attr.transactionAttributeId), "INVOICE_NUMBER", TransactionAttributeType.STRING, "INV-002"), 10.seconds)
          updated.map(_.value) should equal(Full("INV-002"))

          val byTransaction = Await.result(
            TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributes(bankId, transactionId), 10.seconds)
          byTransaction.map(_.map(_.value)) should equal(Full(List("INV-002")))

          val deleted = Await.result(
            TransactionAttributeX.transactionAttributeProvider.vend.deleteTransactionAttribute(attr.transactionAttributeId), 10.seconds)
          deleted should equal(Full(true))

          val afterDelete = Await.result(
            TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributeById(attr.transactionAttributeId), 10.seconds)
          afterDelete.isDefined should equal(false)
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getTransactionIdsByAttributeNameValues matches any row with a requested name/value pair") {
      val bankId = BankId(APIUtil.generateUUID())
      val transactionA = TransactionId(APIUtil.generateUUID())
      val transactionB = TransactionId(APIUtil.generateUUID())
      val transactionC = TransactionId(APIUtil.generateUUID())

      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionA, None, "CATEGORY", TransactionAttributeType.STRING, "FOOD"), 10.seconds)
      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionB, None, "CATEGORY", TransactionAttributeType.STRING, "TRAVEL"), 10.seconds)
      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionC, None, "CATEGORY", TransactionAttributeType.STRING, "OTHER"), 10.seconds)

      val matched = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.getTransactionIdsByAttributeNameValues(
          bankId, Map("CATEGORY" -> List("FOOD"))), 10.seconds)
      matched match {
        case Full(ids) =>
          ids should contain(transactionA.value)
          ids should not contain transactionB.value
        case other => fail(s"expected Full, got $other")
      }

      val matchedMulti = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.getTransactionIdsByAttributeNameValues(
          bankId, Map("CATEGORY" -> List("FOOD", "TRAVEL"))), 10.seconds)
      matchedMulti match {
        case Full(ids) =>
          ids should contain(transactionA.value)
          ids should contain(transactionB.value)
          ids should not contain transactionC.value
        case other => fail(s"expected Full, got $other")
      }

      val matchedEmpty = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.getTransactionIdsByAttributeNameValues(bankId, Map.empty), 10.seconds)
      matchedEmpty match {
        case Full(ids) =>
          ids should contain(transactionA.value)
          ids should contain(transactionB.value)
          ids should contain(transactionC.value)
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getTransactionAttributesCanBeSeenOnView only returns attributes whose definition allows the view") {
      val bankId = BankId(APIUtil.generateUUID())
      val transactionId = TransactionId(APIUtil.generateUUID())
      val ownerView = ViewId("owner")
      val otherView = ViewId("_other")

      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        bankId, "VISIBLE_ATTR", AttributeCategory.Transaction, AttributeType.STRING, "desc", "alias",
        List(ownerView.value), isActive = true), 10.seconds)
      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        bankId, "HIDDEN_ATTR", AttributeCategory.Transaction, AttributeType.STRING, "desc", "alias",
        List(otherView.value), isActive = true), 10.seconds)

      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionId, None, "VISIBLE_ATTR", TransactionAttributeType.STRING, "v1"), 10.seconds)
      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionId, None, "HIDDEN_ATTR", TransactionAttributeType.STRING, "v2"), 10.seconds)

      val visible = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributesCanBeSeenOnView(
          bankId, transactionId, ownerView), 10.seconds)
      visible match {
        case Full(attrs) =>
          attrs.map(_.name) should equal(List("VISIBLE_ATTR"))
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getTransactionsAttributesCanBeSeenOnView filters across multiple transactions") {
      val bankId = BankId(APIUtil.generateUUID())
      val transactionA = TransactionId(APIUtil.generateUUID())
      val transactionB = TransactionId(APIUtil.generateUUID())
      val ownerView = ViewId("owner")

      Await.result(AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
        bankId, "MULTI_ATTR", AttributeCategory.Transaction, AttributeType.STRING, "desc", "alias",
        List(ownerView.value), isActive = true), 10.seconds)

      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionA, None, "MULTI_ATTR", TransactionAttributeType.STRING, "a1"), 10.seconds)
      Await.result(TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
        bankId, transactionB, None, "MULTI_ATTR", TransactionAttributeType.STRING, "b1"), 10.seconds)

      val result = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.getTransactionsAttributesCanBeSeenOnView(
          bankId, List(transactionA, transactionB), ownerView), 10.seconds)
      result match {
        case Full(attrs) =>
          attrs.map(_.value).toSet should equal(Set("a1", "b1"))
        case other => fail(s"expected Full, got $other")
      }

      val emptyResult = Await.result(
        TransactionAttributeX.transactionAttributeProvider.vend.getTransactionsAttributesCanBeSeenOnView(
          bankId, Nil, ownerView), 10.seconds)
      emptyResult should equal(Full(Nil))
    }
  }
}
