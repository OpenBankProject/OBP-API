package code.users

import code.api.util.ErrorMessages

import java.util.Date
import code.util.MappedUUID
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.UserAttributeTrait
import com.openbankproject.commons.model.enums.UserAttributeType
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object MappedUserAttributeProvider extends UserAttributeProvider {
  override def getUserAttributesByUser(userId: String): Future[Box[List[UserAttribute]]] = Future {
    tryo(
      UserAttribute.findAll(By(UserAttribute.UserId, userId))
    )
  }
  override def getPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]] = Future {
    tryo(
      UserAttribute.findAll(
        By(UserAttribute.UserId, userId),
        By(UserAttribute.IsPersonal, true),
        OrderBy(UserAttribute.createdAt, Descending)
      )
    )
  }
  override def getNonPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]] = Future {
    tryo(
      UserAttribute.findAll(
        By(UserAttribute.UserId, userId),
        By(UserAttribute.IsPersonal, false),
        OrderBy(UserAttribute.createdAt, Descending)
      )
    )
  }

  override def getUserAttributesByUsers(userIds: List[String]): Future[Box[List[UserAttribute]]] = Future {
    tryo(
      UserAttribute.findAll(ByList(UserAttribute.UserId, userIds))
    )
  }
  
  override def deleteUserAttribute(userAttributeId: String): Future[Box[Boolean]] = {
    Future {
      UserAttribute.find(By(UserAttribute.UserAttributeId, userAttributeId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.UserAttributeNotFound
        case _ => Full(false)
      }
    }
  }

  override def createOrUpdateUserAttribute(userId: String,
                                           userAttributeId: Option[String],
                                           name: String,
                                           attributeType: UserAttributeType.Value,
                                           value: String,
                                           isPersonal: Boolean): Future[Box[UserAttribute]] = {
    userAttributeId match {
      case Some(id) => Future {
        UserAttribute.find(By(UserAttribute.UserAttributeId, id)) match {
          case Full(attribute) => tryo {
            attribute
              .UserId(userId)
              .Name(name)
              .Type(attributeType.toString)
              .`Value`(value)
//              .IsPersonal(isPersonal) //Can not update this field in update ne
              .saveMe()
          }
          case _ => Empty
        }
      }
      case None => Future {
        Full {
          UserAttribute.create
            .UserId(userId)
            .Name(name)
            .Type(attributeType.toString())
            .`Value`(value)
            .IsPersonal(isPersonal)
            .saveMe()
        }
      }
    }
  }
  
}

class UserAttribute extends UserAttributeTrait with LongKeyedMapper[UserAttribute] with IdPK with CreatedUpdated {

  override def getSingleton = UserAttribute
  object UserAttributeId extends MappedUUID(this)
  object UserId extends MappedUUID(this)
  object Name extends MappedString(this, 255)
  object Type extends MappedString(this, 50)
  object `Value` extends MappedString(this, 255)
  object IsPersonal extends MappedBoolean(this) {
    override def defaultValue = true
  }

  override def userAttributeId: String = UserAttributeId.get
  override def userId: String = UserId.get
  override def name: String = Name.get
  override def attributeType: UserAttributeType.Value = UserAttributeType.withName(Type.get)
  override def value: String = `Value`.get
  override def insertDate: Date = createdAt.get
  override def isPersonal: Boolean = IsPersonal.get
}

object UserAttribute extends UserAttribute with LongKeyedMetaMapper[UserAttribute] {
  override def dbIndexes: List[BaseIndex[UserAttribute]] = Index(UserAttributeId) :: super.dbIndexes
}

