package code.customeraccountlinks

import code.api.util.ErrorMessages
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedCustomerAccountLinkProvider extends CustomerAccountLinkProvider {
  def createCustomerAccountLink(customerId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] = {

    val createCustomerAccountLink = CustomerAccountLink.create
      .CustomerId(customerId)
      .AccountId(accountId)
      .RelationshipType(relationshipType)
      .saveMe()

    Some(createCustomerAccountLink)
  }
  def getOCreateCustomerAccountLink(customerId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] = {
    getCustomerAccountLink(accountId, customerId) match {
      case Empty =>
        val createCustomerAccountLink = CustomerAccountLink.create
          .CustomerId(customerId)
          .AccountId(accountId)
          .RelationshipType(relationshipType)
          .saveMe()
        Some(createCustomerAccountLink)
      case everythingElse => everythingElse
    }
  }

  def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait] = {
    CustomerAccountLink.find(
      By(CustomerAccountLink.CustomerId, customerId))
  }
  def getCustomerAccountLinksByCustomerId(customerId: String): List[CustomerAccountLinkTrait] = {
    CustomerAccountLink.findAll(
      By(CustomerAccountLink.CustomerId, customerId))
  }

  def getCustomerAccountLinksByAccountId(accountId: String): List[CustomerAccountLinkTrait] = {
    val customerAccountLinks : List[CustomerAccountLinkTrait] = CustomerAccountLink.findAll(
      By(CustomerAccountLink.AccountId, accountId)).sortWith(_.id.get < _.id.get)
    customerAccountLinks
  }

  def getCustomerAccountLink(accountId : String, customerId: String): Box[CustomerAccountLinkTrait] = {
    CustomerAccountLink.find(
      By(CustomerAccountLink.AccountId, accountId),
      By(CustomerAccountLink.CustomerId, customerId))
  }

  def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]] = {
    Full(CustomerAccountLink.findAll())
  }

  def bulkDeleteCustomerAccountLinks(): Boolean = {
    CustomerAccountLink.bulkDelete_!!()
  }

  def deleteCustomerAccountLink(customerAccountLinkId: String): Future[Box[Boolean]] = {
    Future {
      CustomerAccountLink.find(By(CustomerAccountLink.CustomerAccountLinkId, customerAccountLinkId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
        case Failure(msg, exception, chain) => Failure(msg, exception, chain)
      }
    }
  }
}

class CustomerAccountLink extends CustomerAccountLinkTrait with LongKeyedMapper[CustomerAccountLink] with IdPK with CreatedUpdated {

  def getSingleton = CustomerAccountLink

  object CustomerAccountLinkId extends MappedUUID(this)
  object CustomerId extends UUIDString(this)
  object AccountId extends UUIDString(this)
  object RelationshipType extends MappedString(this, 255)

  override def customerAccountLinkId: String = CustomerAccountLinkId.get
  override def customerId: String = CustomerId.get // id.toString
  override def accountId: String = AccountId.get
  override def relationshipType: String = RelationshipType.get
}

object CustomerAccountLink extends CustomerAccountLink with LongKeyedMetaMapper[CustomerAccountLink] {
  override def dbIndexes = UniqueIndex(CustomerAccountLinkId) :: UniqueIndex(AccountId, CustomerId) :: super.dbIndexes

}
