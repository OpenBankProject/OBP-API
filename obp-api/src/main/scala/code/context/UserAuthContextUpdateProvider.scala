package code.context

import code.api.util.APIUtil
import code.remotedata.RemotedataUserAuthContextUpdate
import com.openbankproject.commons.model.UserAuthContextUpdate
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object UserAuthContextUpdateProvider extends SimpleInjector {

  val userAuthContextUpdateProvider = new Inject(buildOne _) {}

  def buildOne: UserAuthContextUpdateProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserAuthContextUpdateProvider
      case true => RemotedataUserAuthContextUpdate   // We will use Akka as a middleware
    }
}

trait UserAuthContextUpdateProvider {
  def createUserAuthContextUpdates(userId: String, key: String, value: String): Future[Box[UserAuthContextUpdate]]
  def getUserAuthContextUpdates(userId: String): Future[Box[List[UserAuthContextUpdate]]]
  def getUserAuthContextUpdatesBox(userId: String): Box[List[UserAuthContextUpdate]]
  def deleteUserAuthContextUpdates(userId: String): Future[Box[Boolean]]
  def deleteUserAuthContextUpdateById(authContextUpdateId: String): Future[Box[Boolean]]
  def checkAnswer(authContextUpdateId: String, challenge: String): Future[Box[UserAuthContextUpdate]]
}

class RemotedataUserAuthContextUpdateCaseClasses {
  case class createUserAuthContextUpdate(userId: String, key: String, value: String)
  case class getUserAuthContextUpdates(userId: String)
  case class getUserAuthContextUpdatesBox(userId: String)
  case class deleteUserAuthContextUpdates(userId: String)
  case class deleteUserAuthContextUpdateById(authContextUpdateId: String)
  case class checkAnswer(authContextUpdateId: String, challenge: String)
}

object RemotedataUserAuthContextUpdateCaseClasses extends RemotedataUserAuthContextUpdateCaseClasses