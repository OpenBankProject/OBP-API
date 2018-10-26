package code.context

import code.api.util.CallContext
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.By
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedUserAuthContextProvider extends UserAuthContextProvider with MdcLoggable
{
  
  override def createUserAuthContext(userId: String, key: String, value: String, callContext: Option[CallContext]) = Future 
  {
    val userAuthContext = MappedUserAuthContext.create.mUserId(userId).mKey(key).mValue(value).saveMe()
    (Full((userAuthContext,callContext)))
  }
  
  override def getUserAuthContexts(userId: String, callContext: Option[CallContext])= Future{
    Full(MappedUserAuthContext.findAll(By(MappedUserAuthContext.mUserId, userId))).map(userAuthContexts => (userAuthContexts, callContext))
  }
  
  
}

