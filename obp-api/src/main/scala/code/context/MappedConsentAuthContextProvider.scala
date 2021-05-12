package code.context

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BasicUserAuthContext
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object MappedConsentAuthContextProvider extends ConsentAuthContextProvider with MdcLoggable {
  
  override def createConsentAuthContext(consentId: String, key: String, value: String): Future[Box[MappedConsentAuthContext]] =
    Future {
      createConsentAuthContextAkka(consentId, key, value)
    }
  def createConsentAuthContextAkka(consentId: String, key: String, value: String): Box[MappedConsentAuthContext] =
    tryo {
      MappedConsentAuthContext.create.ConsentId(consentId).Key(key).Value(value).saveMe()
    }

  override def getConsentAuthContexts(consentId: String): Future[Box[List[MappedConsentAuthContext]]] = Future {
    getConsentAuthContextsBox(consentId)
  }
  override def getConsentAuthContextsBox(consentId: String): Box[List[MappedConsentAuthContext]] = {
    tryo {
      MappedConsentAuthContext.findAll(By(MappedConsentAuthContext.ConsentId, consentId))
    }
  }
  // This function creates or replaces only user auth contexts provided a parameter to this function. (It does not delete other user auth contexts)
  // For this reason developers are encouraged to use name space in the key.
  override def createOrUpdateConsentAuthContexts(consentId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[MappedConsentAuthContext]] = {
    // Remove duplicates if any
    val userAuthContextsDistinct = userAuthContexts.distinct
    // Find the user auth contexts we must create
    val create = userAuthContextsDistinct.filter( authContext =>
      MappedConsentAuthContext.find(
        By(MappedConsentAuthContext.ConsentId, consentId),
        By(MappedConsentAuthContext.Key, authContext.key)
      ).isEmpty
    )
    // Find the user auth contexts we must update
    val update = userAuthContextsDistinct diff create // List(1,2,3,4,5) diff List(4,5) = List(1,2,3)

    val updated = update.flatMap( authContext =>
      MappedConsentAuthContext.find(
        By(MappedConsentAuthContext.ConsentId, consentId),
        By(MappedConsentAuthContext.Key, authContext.key)
      ).map( authContext =>
        authContext.Key(authContext.key).Value(authContext.value).saveMe()
      )
    )
    val created = create.map( authContext =>
      MappedConsentAuthContext.create.ConsentId(consentId).Key(authContext.key).Value(authContext.value).saveMe()
    )
    tryo {
      updated ::: created
    }
  }

  def deleteConsentAuthContextsAkka(consentId: String): Box[Boolean] =
    tryo{MappedConsentAuthContext.bulkDelete_!!(By(MappedConsentAuthContext.ConsentId, consentId))}

  override def deleteConsentAuthContexts(userId: String): Future[Box[Boolean]] =
    Future(deleteConsentAuthContextsAkka(userId))

  def deleteConsentAuthContextByIdAkka(consentAuthContextId: String): Box[Boolean] =
    MappedConsentAuthContext.find(By(MappedConsentAuthContext.ConsentAuthContextId, consentAuthContextId)) match {
      case Full(userAuthContext) => Full(userAuthContext.delete_!)
      case Empty => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
      case _ => Full(false)
    }

  override def deleteConsentAuthContextById(userAuthContextId: String): Future[Box[Boolean]] =
    Future(deleteConsentAuthContextByIdAkka(userAuthContextId))
}

