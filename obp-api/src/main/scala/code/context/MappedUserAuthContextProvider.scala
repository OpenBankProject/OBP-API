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
  override def getOrCreateUserAuthContexts(userId: String, userAuthContext: List[BasicUserAuthContext]): Box[List[MappedUserAuthContext]] = {
    val create = userAuthContext.filter( authContext =>
      MappedUserAuthContext.findAll(
        By(MappedUserAuthContext.mUserId, userId),
        By(MappedUserAuthContext.mKey, authContext.key)
      ).isEmpty
    )
    val update = userAuthContext.filterNot( authContext =>
      MappedUserAuthContext.findAll(
        By(MappedUserAuthContext.mUserId, userId),
        By(MappedUserAuthContext.mKey, authContext.key)
      ).isEmpty
    )
    val created = update.map( authContext =>
      MappedUserAuthContext.findAll(
        By(MappedUserAuthContext.mUserId, userId),
        By(MappedUserAuthContext.mKey, authContext.key)
      ).map( authContext =>
        authContext.mKey(authContext.key).mValue(authContext.value).saveMe()
      )
    ).flatten
    val updated = create.map( authContext =>
      MappedUserAuthContext.findAll(
        By(MappedUserAuthContext.mUserId, userId),
        By(MappedUserAuthContext.mKey, authContext.key)
      ).map( authContext =>
        MappedUserAuthContext.create.mUserId(userId).mKey(authContext.key).mValue(authContext.value).saveMe()
      )
    ).flatten
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

