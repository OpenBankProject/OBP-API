package code.context

import code.api.util.CallContext
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object UserAuthContextProvider extends SimpleInjector {

  val userAuthContextProvider = new Inject(buildOne _) {}

  def buildOne: UserAuthContextProvider = MappedUserAuthContextProvider

}

trait UserAuthContextProvider {
  
  def createUserAuthContext(userId: String, key: String, value: String, callContext: Option[CallContext]): Future[Box[(UserAuthContext,Option[CallContext])]]
  
  def getUserAuthContexts(userId: String, callContext: Option[CallContext]):Future[Box[(List[UserAuthContext], Option[CallContext])]]
  
}
