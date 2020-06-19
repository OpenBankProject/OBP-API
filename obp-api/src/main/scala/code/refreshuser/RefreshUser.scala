package code.refreshuser

import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector

object RefreshUser extends SimpleInjector {

  val RefreshUser = new Inject(buildOne _) {}

  def buildOne: RefreshUserProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedRefreshUserProvider
      case true => MappedRefreshUserProvider //RemotedataScopes     // We will use Akka as a middleware
    }
}

//This is used to control the refresh user process.
// refresh_user.interval props will control how often to make it
trait RefreshUser {
  def userId : String
}

trait RefreshUserProvider {
  def needToRefreshUser(userId: String):Boolean
  
}

class RemotedataRefreshUserCaseClasses {
  case class needToRefreshUser(userId: String)
}

object RemotedataRefreshUserCaseClasses extends RemotedataRefreshUserCaseClasses