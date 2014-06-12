package code.users

import net.liftweb.common.Box
import net.liftweb.util.Helpers._
import code.model.User
import code.model.dataAccess.APIUser
import net.liftweb.mapper.By

object LiftUsers extends Users {

  def getUserByApiId(id : String) : Box[User] = {
    for{
      idAsLong <- tryo { id.toLong } ?~ "Invalid id: long required"
      user <- APIUser.find(By(APIUser.id, idAsLong)) ?~ { s"user $id not found"}
    } yield user
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    APIUser.find(By(APIUser.provider_, provider), By(APIUser.providerId, idGivenByProvider))
  }
  
}