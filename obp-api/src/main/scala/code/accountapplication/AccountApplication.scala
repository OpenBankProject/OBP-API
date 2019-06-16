package code.accountapplication


import code.api.util.APIUtil
import code.remotedata.RemotedataAccountApplication
import com.openbankproject.commons.model.{AccountApplication, ProductCode}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object AccountApplicationX extends SimpleInjector {

  val accountApplication = new Inject(buildOne _) {}

  def buildOne: AccountApplicationProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedAccountApplicationProvider
      case true => RemotedataAccountApplication
    }
}

trait AccountApplicationProvider {
  def getAll(): Future[Box[List[AccountApplication]]]
  def getById(accountApplicationId: String): Future[Box[AccountApplication]]
  def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String]): Future[Box[AccountApplication]]
  def updateStatus(accountApplicationId:String, status: String): Future[Box[AccountApplication]]
}


class RemotedataAccountApplicationCaseClasses {
  case class getAll()
  case class getById(accountApplicationId: String)
  case class createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String])
  case class updateStatus(accountApplicationId:String, status: String)
}

object RemotedataAccountApplicationCaseClasses extends RemotedataAccountApplicationCaseClasses



