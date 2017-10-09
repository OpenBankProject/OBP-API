package code.users

import code.api.GatewayLogin.gateway
import net.liftweb.common.{Box, Full}
import code.model.User
import code.model.dataAccess.{ResourceUser, ResourceUserCaseClass}
import net.liftweb.mapper.By

import scala.collection.immutable.List
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object LiftUsers extends Users {

  //UserId here is the resourceuser.id field
  def getUserByResourceUserId(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  //UserId here is the resourceuser.id field
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdF(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]] = {
    Future{getResourceUserByResourceUserIdF(id)}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    ResourceUser.find(By(ResourceUser.provider_, provider), By(ResourceUser.providerId, idGivenByProvider))
  }
  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] = {
    Future {
      getUserByProviderId(provider, idGivenByProvider)
    }
  }

  def getOrCreateUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = idGivenByProvider).or { // Find a user
      Users.users.vend.createResourceUser( // Otherwise create a new one
        provider = gateway,
        providerId = Some(idGivenByProvider),
        name = Some(idGivenByProvider),
        email = None,
        userId = None
      )
    }
  }
  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] = {
    Future {
      getOrCreateUserByProviderId(provider, idGivenByProvider)
    }
  }

  def getUserByUserId(userId : String) : Box[User] = {
    ResourceUser.find(By(ResourceUser.userId_, userId))
  }

  override def getUserByUserName(userName: String): Box[ResourceUser] = {
    ResourceUser.find(By(ResourceUser.name_, userName))
  }

  override def getUserByEmail(email: String): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAll(By(ResourceUser.email, email)))
  }

  override def getAllUsers(): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAll())
  }

  override def getAllUsersF(): Future[Box[List[ResourceUserCaseClass]]] = {
    val users = Full(ResourceUser.findAll().map(_.toCaseClass))
    Future{users}
  }

  def getAllUsersFF(): Box[List[ResourceUserCaseClass]] = {
    val users = Full(ResourceUser.findAll().map(_.toCaseClass))
    users
  }

  override def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]): Box[ResourceUser] = {
    val ru = ResourceUser.create
    ru.provider_(provider)
    providerId match {
      case Some(v) => ru.providerId(v)
      case None    =>
    }
    name match {
      case Some(v) => ru.name_(v)
      case None    =>
    }
    email match {
      case Some(v) => ru.email(v)
      case None    =>
    }
    userId match {
      case Some(v) => ru.userId_(v)
      case None    =>
    }
    Full(ru.saveMe())
  }

  override def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]): Box[ResourceUser] = {
    val ru = ResourceUser.create
    ru.provider_(provider)
    providerId match {
      case Some(v) => ru.providerId(v)
      case None    =>
    }
    name match {
      case Some(v) => ru.name_(v)
      case None    =>
    }
    email match {
      case Some(v) => ru.email(v)
      case None    =>
    }
    userId match {
      case Some(v) => ru.userId_(v)
      case None    =>
    }
    Full(ru)
  }

  override def saveResourceUser(ru: ResourceUser): Box[ResourceUser] = {
    val r = Full(ru.saveMe())
    r
  }

  override def bulkDeleteAllResourceUsers(): Box[Boolean] = {
    Full( ResourceUser.bulkDelete_!!() )
  }

  override def deleteResourceUser(userId: Long): Box[Boolean] = {
    for {
      u <- ResourceUser.find(By(ResourceUser.id, userId))
    } yield {
      u.delete_!
    }
  }
  
}
