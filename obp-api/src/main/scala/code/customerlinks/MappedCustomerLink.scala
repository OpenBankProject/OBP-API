package code.customerlinks

import java.util.Date

import code.api.util.ErrorMessages
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedCustomerLinkProvider extends CustomerLinkProvider {
  override def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLink] = {
    tryo {
      MappedCustomerLink.create
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mOtherBankId(otherBankId)
        .mOtherCustomerId(otherCustomerId)
        .mRelationshipTo(relationshipTo)
        .saveMe()
    }
  }

  override def getCustomerLinkById(customerLinkId: String): Box[CustomerLink] = {
    MappedCustomerLink.find(
      By(MappedCustomerLink.mCustomerLinkId, customerLinkId)
    )
  }

  override def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLink]] = {
    tryo {
      MappedCustomerLink.findAll(
        By(MappedCustomerLink.mBankId, bankId))
    }
  }

  override def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLink]] = {
    tryo {
      MappedCustomerLink.findAll(
        By(MappedCustomerLink.mCustomerId, customerId))
    }
  }

  override def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLink] = {
    MappedCustomerLink.find(By(MappedCustomerLink.mCustomerLinkId, customerLinkId)) match {
      case Full(t) => Full(t.mRelationshipTo(relationshipTo).saveMe())
      case Empty => Empty ?~! ErrorMessages.CustomerLinkNotFound
      case Failure(msg, exception, chain) => Failure(msg, exception, chain)
    }
  }

  override def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]] = {
    Future {
      MappedCustomerLink.find(By(MappedCustomerLink.mCustomerLinkId, customerLinkId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.CustomerLinkNotFound
        case Failure(msg, exception, chain) => Failure(msg, exception, chain)
      }
    }
  }

  override def bulkDeleteCustomerLinks(): Boolean = {
    MappedCustomerLink.bulkDelete_!!()
  }
}

class MappedCustomerLink extends CustomerLink with LongKeyedMapper[MappedCustomerLink] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerLink

  object mCustomerLinkId extends MappedUUID(this)
  object mBankId extends MappedString(this, 255)
  object mCustomerId extends UUIDString(this)
  object mOtherBankId extends MappedString(this, 255)
  object mOtherCustomerId extends UUIDString(this)
  object mRelationshipTo extends MappedString(this, 255)

  override def customerLinkId: String = mCustomerLinkId.get
  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
  override def otherBankId: String = mOtherBankId.get
  override def otherCustomerId: String = mOtherCustomerId.get
  override def relationshipTo: String = mRelationshipTo.get
  override def dateInserted: Date = createdAt.get
  override def dateUpdated: Date = updatedAt.get
}

object MappedCustomerLink extends MappedCustomerLink with LongKeyedMetaMapper[MappedCustomerLink] {
  override def dbIndexes = UniqueIndex(mCustomerLinkId) :: Index(mCustomerId) :: Index(mOtherCustomerId) :: super.dbIndexes
}
