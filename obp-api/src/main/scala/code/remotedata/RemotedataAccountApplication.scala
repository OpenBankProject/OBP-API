package code.remotedata

import akka.pattern.ask
import code.accountapplication.{AccountApplicationProvider, RemotedataAccountApplicationCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model.{AccountApplication, ProductCode}
import net.liftweb.common.Box

import scala.concurrent.Future


object RemotedataAccountApplication extends ObpActorInit with AccountApplicationProvider {

  val cc = RemotedataAccountApplicationCaseClasses

  override def getAll(): Future[Box[List[AccountApplication]]] = 
    (actor ? cc.getAll()).mapTo[Box[List[AccountApplication]]]

  override def getById(accountApplicationId: String): Future[Box[AccountApplication]] = 
    (actor ? cc.getById(accountApplicationId)).mapTo[Box[AccountApplication]]

  override def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String]): Future[Box[AccountApplication]] = 
    (actor ? cc.createAccountApplication(productCode, userId, customerId)).mapTo[Box[AccountApplication]]

  override def updateStatus(accountApplicationId: String, status: String): Future[Box[AccountApplication]] = 
    (actor ? cc.updateStatus(accountApplicationId, status)).mapTo[Box[AccountApplication]]
}
