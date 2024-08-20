package code.UserRefreshes

import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector

object UserRefreshes extends SimpleInjector {

  val UserRefreshes = new Inject(buildOne _) {}

  def buildOne: UserRefreshesProvider = MappedUserRefreshesProvider
}

//This is used to control the refresh user process.
// refresh_user.interval props will control how often to make it
trait UserRefreshes {
  def userId : String
}

trait UserRefreshesProvider {
  // This method will check if we need to refresh user or not..
  def needToRefreshUser(userId: String):Boolean

  def createOrUpdateRefreshUser(userId: String):UserRefreshes
  
}