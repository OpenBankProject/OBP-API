package code.UserRefreshes

import java.util.{Calendar, Date}

import code.api.util.APIUtil
import code.util.UUIDString
import net.liftweb.common.Full
import net.liftweb.mapper._

object MappedUserRefreshesProvider extends UserRefreshesProvider {

  //This method will check if we need to refresh user or not..
  //1st: check if last update is empty or not,
  // if empty --> UserRefreshes/true
  // if not empty, compare last update and the props interval--> 
  //    --> if (lastUpdate + interval) >= current -->  UserRefreshes/true
  //    --> if (lastUpdate + interval) < current -->  false 
  override def needToRefreshUser(userId: String) =  {
    MappedUserRefreshes.find(By(MappedUserRefreshes.mUserId, userId)) match {
      case Full(user) =>{
        val UserRefreshesInterval = APIUtil.getPropsAsIntValue("refresh_user.interval", 43200)
        val lastUpdate: Date = user.updatedAt.get
        val lastUpdatePlusInterval: Calendar = Calendar.getInstance()
        lastUpdatePlusInterval.setTime(lastUpdate)
        lastUpdatePlusInterval.add(Calendar.MINUTE, UserRefreshesInterval)
        val currentDate = Calendar.getInstance()
        lastUpdatePlusInterval.before(currentDate)
      }
      case _ => true
    }
  }

}

class MappedUserRefreshes extends UserRefreshes with LongKeyedMapper[MappedUserRefreshes] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserRefreshes

  object mUserId extends UUIDString(this)
  override def userId: String = mUserId.get
}

object MappedUserRefreshes extends MappedUserRefreshes with LongKeyedMetaMapper[MappedUserRefreshes] {
  override def dbIndexes = UniqueIndex(mUserId) :: super.dbIndexes
}