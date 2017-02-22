package code.users

import net.liftweb.common.{Box, Full}
import code.model.User
import code.model.dataAccess.ResourceUser
import net.liftweb.mapper.By

import scala.collection.immutable.List

object LiftUsers extends Users {

  def getUserByResourceUserId(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    ResourceUser.find(By(ResourceUser.provider_, provider), By(ResourceUser.providerId, idGivenByProvider))
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
  
}