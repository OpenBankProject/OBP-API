package code.context

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BasicUserAuthContext

import scala.collection.immutable.List
import scala.concurrent.Future

object MappedUserAuthContextProvider extends UserAuthContextProvider with MdcLoggable {
  
  override def createUserAuthContext(userId: String, key: String, value: String): Future[Box[MappedUserAuthContext]] =
    Future {
      createUserAuthContextAkka(userId, key, value)
    }
  def createUserAuthContextAkka(userId: String, key: String, value: String): Box[MappedUserAuthContext] =
    tryo {
      MappedUserAuthContext.create.mUserId(userId).mKey(key).mValue(value).saveMe()
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

