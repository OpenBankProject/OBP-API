package code.users

import code.util.MappedUUID
import com.openbankproject.commons.model.enums.{AccountAttributeType, UserAttributeType}
import com.openbankproject.commons.model.{AccountAttribute, UserAttributeTrait}
import net.liftweb.common.Box
import net.liftweb.mapper._

import scala.collection.immutable.List
import scala.concurrent.Future


object MappedUserAttributeProvider extends UserAttributeProvider {
  override def getAccountAttributesByUser(userId: String): Future[Box[List[UserAttribute]]] = ???

  override def createOrUpdateUserAttribute(userId: String,
                                           userAttributeId: Option[String],
                                           name: String,
                                           attributeType: UserAttributeType.Value,
                                           value: String): Future[Box[UserAttribute]] = ???
}

class UserAttribute extends UserAttributeTrait with LongKeyedMapper[UserAttribute] with IdPK {

  override def getSingleton = UserAttribute
  object UserAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 50)
  object Type extends MappedString(this, 50)
  object Value extends MappedString(this, 255)

  override def userAttributeId: String = UserAttributeId.get
  override def name: String = Name.get
  override def attributeType: UserAttributeType.Value = UserAttributeType.withName(Type.get)
  override def value: String = Value.get
}

object UserAttribute extends UserAttribute with LongKeyedMetaMapper[UserAttribute] {
  override def dbIndexes: List[BaseIndex[UserAttribute]] = Index(UserAttributeId) :: super.dbIndexes
}

