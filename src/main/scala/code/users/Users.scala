package code.users

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model.User
import code.model.dataAccess.ResourceUser
import code.remotedata.RemotedataUsers

object Users  extends SimpleInjector {

  val users = new Inject(buildOne _) {}
  
  //def buildOne: Users = LiftUsers
  def buildOne: Users = RemotedataUsers
  
}

trait Users {
  //resourceuser has two ids: id(Long)and userid_(String), this method use id(Long)
  def getUserByResourceUserId(id : Long) : Box[User]

  //resourceuser has two ids: id(Long)and userid_(String), this method use id(Long)
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser]

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User]

  //resourceuser has two ids: id(Long)and userid_(String), this method use userid_(String)
  def getUserByUserId(userId : String) : Box[User]

  // find ResourceUser by Resourceuser user name 
  def getUserByUserName(userName: String) : Box[ResourceUser]

  def getUserByEmail(email: String) : Box[List[ResourceUser]]

  def getAllUsers() : Box[List[ResourceUser]]

  def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser]

  def deleteResourceUser(userId: Long) : Box[Boolean]

  def bulkDeleteAllResourceUsers() : Box[Boolean]
}

class RemotedataUsersCaseClasses {
  case class getUserByResourceUserId(id : Long)
  case class getResourceUserByResourceUserId(id : Long)
  case class getUserByProviderId(provider : String, idGivenByProvider : String)
  case class getUserByUserId(userId : String)
  case class getUserByUserName(userName : String)
  case class getUserByEmail(email : String)
  case class getAllUsers()
  case class createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String])
  case class createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String])
  case class saveResourceUser(resourceUser: ResourceUser)
  case class deleteResourceUser(userId: Long)
  case class bulkDeleteAllResourceUsers()
}

object RemotedataUsersCaseClasses extends RemotedataUsersCaseClasses
