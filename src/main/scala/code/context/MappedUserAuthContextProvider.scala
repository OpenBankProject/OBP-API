package code.context

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.PrettyPrinter

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

