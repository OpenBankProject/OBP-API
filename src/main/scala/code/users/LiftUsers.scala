package code.users

import net.liftweb.common.Box
import net.liftweb.util.Helpers._
import code.model.User
import code.model.dataAccess.APIUser
import net.liftweb.mapper.By

private object LiftUsers extends Users {

  def getUserByApiId(id : Long) : Box[User] = {
    APIUser.find(id) ?~ { s"user $id not found"}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    APIUser.find(By(APIUser.provider_, provider), By(APIUser.providerId, idGivenByProvider))
  }
  
}