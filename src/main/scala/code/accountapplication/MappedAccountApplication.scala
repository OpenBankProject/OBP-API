package code.accountapplication

import java.util.Date

import code.api.util.ErrorMessages
import code.customer.{Customer, MappedCustomer}
import code.model.dataAccess.ResourceUser
import code.products.Products.ProductCode
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedAccountApplicationProvider extends AccountApplicationProvider {

  override def getAll(): Future[Box[List[AccountApplication]]] = Future {
    Some(MappedAccountApplication.findAll())
  }

  override def getById(accountApplicationId: String): Future[Box[AccountApplication]] = Future {
    MappedAccountApplication.find(By(MappedAccountApplication.mAccountApplicationId, accountApplicationId))
  }

  override def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String]): Future[Box[AccountApplication]] =
    Future {
      tryo {
        MappedAccountApplication.create.mCode(productCode.value).mUserId(userId.orNull).mCustomerId(customerId.orNull).mStatus("REQUESTED").saveMe()
      }
  }

  override def updateStatus(accountApplicationId:String, status: String): Future[Box[Boolean]] = getById(accountApplicationId).map {
    case Full(accountApplication) => {
      accountApplication.asInstanceOf[MappedAccountApplication].mStatus.set(status)
      Full(true)
    }
    case Empty   => Empty ?~! ErrorMessages.AccountApplicationNotFound
    case _       => Full(false)
  }
}

class MappedAccountApplication extends AccountApplication with LongKeyedMapper[MappedAccountApplication] with IdPK with CreatedUpdated {

  def getSingleton = MappedAccountApplication

  object mAccountApplicationId extends MappedUUID(this)
  object mCode extends MappedString(this, 50)
  object mCustomerId extends MappedUUID(this)
  object mUserId extends MappedUUID(this) //resourceUser
  object mStatus extends MappedString(this, 255)

  override def accountApplicationId: String = mAccountApplicationId.get

  override def productCode: ProductCode = ProductCode(mCode.get)
  override def userId: String = mUserId.get
  override def customerId: String = mCustomerId.get
  override def dateOfApplication: Date = createdAt.get
  override def status: String = mStatus.get

}

object MappedAccountApplication extends MappedAccountApplication with LongKeyedMetaMapper[MappedAccountApplication] {
  override def dbIndexes = UniqueIndex(mAccountApplicationId) :: super.dbIndexes
}
