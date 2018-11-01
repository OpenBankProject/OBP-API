package code.context

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
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
    getUserAuthContextsAkka(userId)
  }
  def getUserAuthContextsAkka(userId: String): Box[List[MappedUserAuthContext]] = {
    tryo {
      MappedUserAuthContext.findAll(By(MappedUserAuthContext.mUserId, userId))
    }
  }

}

