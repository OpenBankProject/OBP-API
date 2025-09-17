package code.users

/* For UserAttribute */

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.UserAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object UserAttributeProvider extends SimpleInjector {

  val userAttributeProvider = new Inject(buildOne _) {}

  def buildOne: UserAttributeProvider = MappedUserAttributeProvider

  // Helper to get the count out of an option
  def countOfUserAttribute(listOpt: Option[List[UserAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }

}

trait UserAttributeProvider extends MdcLoggable {

  def getUserAttributesByUser(userId: String): Future[Box[List[UserAttribute]]]
  def getPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]]
  def getNonPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]]
  def getUserAttributesByUsers(userIds: List[String]): Future[Box[List[UserAttribute]]]
  def deleteUserAttribute(userAttributeId: String): Future[Box[Boolean]]
  def createOrUpdateUserAttribute(userId: String,
                                  userAttributeId: Option[String],
                                  name: String,
                                  attributeType: UserAttributeType.Value,
                                  value: String,
                                  isPersonal: Boolean): Future[Box[UserAttribute]]
  // End of Trait
}
