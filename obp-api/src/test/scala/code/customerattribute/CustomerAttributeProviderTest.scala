package code.customerattribute

import code.api.util.APIUtil
import code.setup.ServerSetup
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, CustomerId}
import net.liftweb.common.Full

import scala.concurrent.Await
import scala.concurrent.duration._

class CustomerAttributeProviderTest extends ServerSetup {

  Feature("CustomerAttributeX provider - CRUD and attribute-name-value filtering") {

    Scenario("create, read, update, delete a single attribute") {
      val bankId = BankId(APIUtil.generateUUID())
      val customerId = CustomerId(APIUtil.generateUUID())

      val created = Await.result(
        CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
          bankId, customerId, None, "TAX_NUMBER", CustomerAttributeType.STRING, "123456"), 10.seconds)
      created match {
        case Full(attr) =>
          attr.name should equal("TAX_NUMBER")
          attr.value should equal("123456")

          val fetched = Await.result(
            CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributeById(attr.customerAttributeId), 10.seconds)
          fetched.map(_.value) should equal(Full("123456"))

          val updated = Await.result(
            CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
              bankId, customerId, Some(attr.customerAttributeId), "TAX_NUMBER", CustomerAttributeType.STRING, "654321"), 10.seconds)
          updated.map(_.value) should equal(Full("654321"))

          val deleted = Await.result(
            CustomerAttributeX.customerAttributeProvider.vend.deleteCustomerAttribute(attr.customerAttributeId), 10.seconds)
          deleted should equal(Full(true))

          val afterDelete = Await.result(
            CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributeById(attr.customerAttributeId), 10.seconds)
          afterDelete.isDefined should equal(false)
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("getCustomerIdsByAttributeNameValues matches any row with a requested name/value pair") {
      val bankId = BankId(APIUtil.generateUUID())
      val customerA = CustomerId(APIUtil.generateUUID())
      val customerB = CustomerId(APIUtil.generateUUID())
      val customerC = CustomerId(APIUtil.generateUUID())

      Await.result(CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
        bankId, customerA, None, "SEGMENT", CustomerAttributeType.STRING, "GOLD"), 10.seconds)
      Await.result(CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
        bankId, customerB, None, "SEGMENT", CustomerAttributeType.STRING, "SILVER"), 10.seconds)
      Await.result(CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
        bankId, customerC, None, "SEGMENT", CustomerAttributeType.STRING, "BRONZE"), 10.seconds)

      val matched = Await.result(
        CustomerAttributeX.customerAttributeProvider.vend.getCustomerIdsByAttributeNameValues(
          bankId, Map("SEGMENT" -> List("GOLD"))), 10.seconds)
      matched match {
        case Full(ids) =>
          ids should contain(customerA.value)
          ids should not contain customerB.value
          ids should not contain customerC.value
        case other => fail(s"expected Full, got $other")
      }

      val matchedMulti = Await.result(
        CustomerAttributeX.customerAttributeProvider.vend.getCustomerIdsByAttributeNameValues(
          bankId, Map("SEGMENT" -> List("GOLD", "SILVER"))), 10.seconds)
      matchedMulti match {
        case Full(ids) =>
          ids should contain(customerA.value)
          ids should contain(customerB.value)
          ids should not contain customerC.value
        case other => fail(s"expected Full, got $other")
      }

      val matchedEmpty = Await.result(
        CustomerAttributeX.customerAttributeProvider.vend.getCustomerIdsByAttributeNameValues(
          bankId, Map.empty), 10.seconds)
      matchedEmpty match {
        case Full(ids) =>
          ids should contain(customerA.value)
          ids should contain(customerB.value)
          ids should contain(customerC.value)
        case other => fail(s"expected Full, got $other")
      }
    }
  }
}
