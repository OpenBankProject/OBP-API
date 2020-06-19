package code.refreshuser

import java.util.{Calendar, Date}

import code.api.util.APIUtil
import code.util.UUIDString
import net.liftweb.common.Full
import net.liftweb.mapper._

object MappedRefreshUserProvider extends RefreshUserProvider {

  //This method will check if we need to refresh user or not..
  //1st: check if last update is empty or not,
  // if empty --> RefreshUser/true
  // if not empty, compare last update and the props interval--> 
  //    --> if (lastUpdate + interval) >= current -->  RefreshUser/true
  //    --> if (lastUpdate + interval) < current -->  false 
  override def needToRefreshUser(userId: String) =  {
    MappedRefreshUser.find(By(MappedRefreshUser.mUserId, userId)) match {
      case Full(user) =>{
        val refreshUserInterval = APIUtil.getPropsAsIntValue("refresh_user.interval", 43200)
        val lastUpdate: Date = user.updatedAt.get
        val lastUpdatePlusInterval: Calendar = Calendar.getInstance()
        lastUpdatePlusInterval.setTime(lastUpdate)
        lastUpdatePlusInterval.add(Calendar.MINUTE, refreshUserInterval)
        val currentDate = Calendar.getInstance()
        lastUpdatePlusInterval.before(currentDate)
      }
      case _ => true
    }
  }

}

class MappedRefreshUser extends RefreshUser with LongKeyedMapper[MappedRefreshUser] with IdPK with CreatedUpdated {

  def getSingleton = MappedRefreshUser

  object mUserId extends UUIDString(this)
  override def userId: String = mUserId.get
}

object MappedRefreshUser extends MappedRefreshUser with LongKeyedMetaMapper[MappedRefreshUser] {
  override def dbIndexes = UniqueIndex(mUserId) :: super.dbIndexes
}