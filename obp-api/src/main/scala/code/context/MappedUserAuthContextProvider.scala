/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.context

import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.CreateUserAuthContextError
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BasicUserAuthContext

import scala.collection.immutable.List
import scala.concurrent.Future

object MappedUserAuthContextProvider extends UserAuthContextProvider with MdcLoggable {
  
  override def createUserAuthContext(userId: String, key: String, value: String, consumerId: String): Future[Box[MappedUserAuthContext]] =
    Future {
      createUserAuthContextAkka(userId, key, value, consumerId)
    }
  def createUserAuthContextAkka(userId: String, key: String, value: String, consumerId: String): Box[MappedUserAuthContext] =
    tryo {
      if(consumerId.isEmpty || consumerId == null){
        throw new RuntimeException(s"$CreateUserAuthContextError current consumerId is empty here.")
      }else{         
        MappedUserAuthContext.create.mUserId(userId).mKey(key).mValue(value).mConsumerId(consumerId).saveMe()
      }
    }

  override def getUserAuthContexts(userId: String): Future[Box[List[MappedUserAuthContext]]] = Future {
    getUserAuthContextsBox(userId)
  }
  override def getUserAuthContextsBox(userId: String): Box[List[MappedUserAuthContext]] = {
    tryo {
      MappedUserAuthContext.findAll(By(MappedUserAuthContext.mUserId, userId))
    }
  }
  // This function creates or replaces only user auth contexts provided a parameter to this function. (It does not delete other user auth contexts)
  // For this reason developers are encouraged to use name space in the key.
  override def createOrUpdateUserAuthContexts(userId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[MappedUserAuthContext]] = {
    // Remove duplicates if any
    val userAuthContextsDistinct = userAuthContexts.distinct
    // Find the user auth contexts we must create
    val create = userAuthContextsDistinct.filter( authContext =>
      MappedUserAuthContext.find(
        By(MappedUserAuthContext.mUserId, userId),
        By(MappedUserAuthContext.mKey, authContext.key)
      ).isEmpty
    )
    // Find the user auth contexts we must update
    val update = userAuthContextsDistinct diff create // List(1,2,3,4,5) diff List(4,5) = List(1,2,3)

    val updated = update.flatMap( authContext =>
      MappedUserAuthContext.find(
        By(MappedUserAuthContext.mUserId, userId),
        By(MappedUserAuthContext.mKey, authContext.key)
      ).map( authContext =>
        authContext.mKey(authContext.key).mValue(authContext.value).saveMe()
      )
    )
    val created = create.map( authContext =>
      MappedUserAuthContext.create.mUserId(userId).mKey(authContext.key).mValue(authContext.value).saveMe()
    )
    tryo {
      updated ::: created
    }
  }

  def deleteUserAuthContextsAkka(userId: String): Box[Boolean] =
    tryo{MappedUserAuthContext.bulkDelete_!!(By(MappedUserAuthContext.mUserId, userId))}

  override def deleteUserAuthContexts(userId: String): Future[Box[Boolean]] =
    Future(deleteUserAuthContextsAkka(userId))

  def deleteUserAuthContextByIdAkka(userAuthContextId: String): Box[Boolean] =
    MappedUserAuthContext.find(By(MappedUserAuthContext.mUserAuthContextId, userAuthContextId)) match {
      case Full(userAuthContext) => Full(userAuthContext.delete_!)
      case Empty => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
      case _ => Full(false)
    }

  override def deleteUserAuthContextById(userAuthContextId: String): Future[Box[Boolean]] =
    Future(deleteUserAuthContextByIdAkka(userAuthContextId))
}

