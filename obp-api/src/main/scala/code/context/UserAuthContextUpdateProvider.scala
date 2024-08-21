package code.context

import code.api.util.APIUtil
import com.openbankproject.commons.model.UserAuthContextUpdate
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object UserAuthContextUpdateProvider extends SimpleInjector {

  val userAuthContextUpdateProvider = new Inject(buildOne _) {}

  def buildOne: UserAuthContextUpdateProvider = MappedUserAuthContextUpdateProvider 
  
}

trait UserAuthContextUpdateProvider {
  def createUserAuthContextUpdates(userId: String, consumerId:String, key: String, value: String): Future[Box[UserAuthContextUpdate]]
  def getUserAuthContextUpdates(userId: String): Future[Box[List[UserAuthContextUpdate]]]
  def getUserAuthContextUpdatesBox(userId: String): Box[List[UserAuthContextUpdate]]
  def deleteUserAuthContextUpdates(userId: String): Future[Box[Boolean]]
  def deleteUserAuthContextUpdateById(authContextUpdateId: String): Future[Box[Boolean]]
  def checkAnswer(authContextUpdateId: String, challenge: String): Future[Box[UserAuthContextUpdate]]
}