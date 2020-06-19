package code.UserRefreshes

import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector

object UserRefreshes extends SimpleInjector {

  val UserRefreshes = new Inject(buildOne _) {}

  def buildOne: UserRefreshesProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserRefreshesProvider
      case true => MappedUserRefreshesProvider //RemotedataScopes     // We will use Akka as a middleware
    }
}

//This is used to control the refresh user process.
// refresh_user.interval props will control how often to make it
trait UserRefreshes {
  def userId : String
}

trait UserRefreshesProvider {
  def needToRefreshUser(userId: String):Boolean
  
}

class RemotedataUserRefreshesCaseClasses {
  case class needToUserRefreshes(userId: String)
}

object RemotedataUserRefreshesCaseClasses extends RemotedataUserRefreshesCaseClasses