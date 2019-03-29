package code.context

import code.api.util.APIUtil
import code.remotedata.RemotedataUserAuthContextRequest
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object UserAuthContextRequestProvider extends SimpleInjector {

  val userAuthContextRequestProvider = new Inject(buildOne _) {}

  def buildOne: UserAuthContextRequestProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserAuthContextRequestProvider
      case true => RemotedataUserAuthContextRequest   // We will use Akka as a middleware
    }
}

trait UserAuthContextRequestProvider {
  def createUserAuthContextRequest(userId: String, key: String, value: String): Future[Box[UserAuthContextRequest]]
  def getUserAuthContextRequests(userId: String): Future[Box[List[UserAuthContextRequest]]]
  def getUserAuthContextRequestsBox(userId: String): Box[List[UserAuthContextRequest]]
  def deleteUserAuthContextRequests(userId: String): Future[Box[Boolean]]
  def deleteUserAuthContextRequestById(userAuthContextId: String): Future[Box[Boolean]]
}

class RemotedataUserAuthContextRequestCaseClasses {
  case class createUserAuthContextRequest(userId: String, key: String, value: String)
  case class getUserAuthContextRequests(userId: String)
  case class getUserAuthContextRequestsBox(userId: String)
  case class deleteUserAuthContextRequests(userId: String)
  case class deleteUserAuthContextRequestById(userAuthContextId: String)
}

object RemotedataUserAuthContextRequestCaseClasses extends RemotedataUserAuthContextRequestCaseClasses