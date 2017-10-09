package code.users

import code.model.User
import code.model.dataAccess.{ResourceUser, ResourceUserCaseClass}
import code.remotedata.RemotedataUsers
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object Users  extends SimpleInjector {

  val users = new Inject(buildOne _) {}

  def buildOne: Users =
    Props.getBool("use_akka", false) match {
      case false  => LiftUsers
      case true => RemotedataUsers     // We will use Akka as a middleware
    }
  
}

trait Users {
  //resourceuser has two ids: id(Long)and userid_(String), this method use id(Long)
  def getUserByResourceUserId(id : Long) : Box[User]

  //resourceuser has two ids: id(Long)and userid_(String), this method use id(Long)
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser]
  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]]

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User]
  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]]
  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]]

  //resourceuser has two ids: id(Long)and userid_(String), this method use userid_(String)
  def getUserByUserId(userId : String) : Box[User]

  // find ResourceUser by Resourceuser user name 
  def getUserByUserName(userName: String) : Box[ResourceUser]

  def getUserByEmail(email: String) : Box[List[ResourceUser]]

  def getAllUsers() : Box[List[ResourceUser]]

  def getAllUsersF() : Future[Box[List[ResourceUserCaseClass]]]

  def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser]

  def deleteResourceUser(userId: Long) : Box[Boolean]

  def bulkDeleteAllResourceUsers() : Box[Boolean]
}

class RemotedataUsersCaseClasses {
  case class getUserByResourceUserId(id : Long)
  case class getResourceUserByResourceUserId(id : Long)
  case class getResourceUserByResourceUserIdFuture(id : Long)
  case class getUserByProviderId(provider : String, idGivenByProvider : String)
  case class getUserByProviderIdFuture(provider : String, idGivenByProvider : String)
  case class getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String)
  case class getUserByUserId(userId : String)
  case class getUserByUserName(userName : String)
  case class getUserByEmail(email : String)
  case class getAllUsers()
  case class getAllUsersF()
  case class createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String])
  case class createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String])
  case class saveResourceUser(resourceUser: ResourceUser)
  case class deleteResourceUser(userId: Long)
  case class bulkDeleteAllResourceUsers()
}

object RemotedataUsersCaseClasses extends RemotedataUsersCaseClasses
