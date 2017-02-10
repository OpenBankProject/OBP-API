package code.users

import net.liftweb.common.Box
import net.liftweb.util.Helpers._
import code.model.User
import code.model.dataAccess.ResourceUser
import net.liftweb.mapper.By

object LiftUsers extends Users {

  def getUserByApiId(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    ResourceUser.find(By(ResourceUser.provider_, provider), By(ResourceUser.providerId, idGivenByProvider))
  }

  def getUserByUserId(userId : String) : Box[User] = {
    ResourceUser.find(By(ResourceUser.userId_, userId))
  }
  
}