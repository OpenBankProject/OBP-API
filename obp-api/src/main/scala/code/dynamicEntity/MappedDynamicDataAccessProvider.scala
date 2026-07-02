package code.DynamicData

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.mutable

object MappedDynamicDataAccessProvider extends DynamicDataAccessProvider {

  override def grant(dynamicDataId: String, userId: String,
                     canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
                     entityName: String, bankId: Option[String], grantedBy: String): Box[DynamicDataAccessT] = tryo {
    val row = DynamicDataAccess.find(
      By(DynamicDataAccess.DynamicDataId, dynamicDataId),
      By(DynamicDataAccess.UserId, userId)
    ).getOrElse(DynamicDataAccess.create.DynamicDataId(dynamicDataId).UserId(userId))
    row.CanRead(canRead)
      .CanUpdate(canUpdate)
      .CanDelete(canDelete)
      .CanGrant(canGrant)
      .EntityName(entityName)
      .BankId(bankId.getOrElse(null))
      .GrantedBy(grantedBy)
      .saveMe()
  }

  override def revoke(dynamicDataId: String, userId: String): Box[Int] = tryo {
    // Walk the GrantedBy edges within this single data row: remove the target user and
    // everyone they granted downstream. The visited-set makes re-share cycles terminate
    // and absorbs the owner row's self-edge (GrantedBy == UserId).
    val toRemove = mutable.LinkedHashSet[String](userId)
    val visited  = mutable.Set[String]()
    var frontier = List(userId)
    while (frontier.nonEmpty) {
      val current = frontier.head
      frontier = frontier.tail
      if (!visited.contains(current)) {
        visited += current
        val children = DynamicDataAccess.findAll(
          By(DynamicDataAccess.DynamicDataId, dynamicDataId),
          By(DynamicDataAccess.GrantedBy, current)
        ).map(_.UserId.get).filterNot(visited.contains)
        children.foreach { child =>
          toRemove += child
          frontier = child :: frontier
        }
      }
    }
    toRemove.toList.flatMap { uid =>
      DynamicDataAccess.findAll(
        By(DynamicDataAccess.DynamicDataId, dynamicDataId),
        By(DynamicDataAccess.UserId, uid)
      )
    }.map(_.delete_!).count(identity)
  }

  override def getAccessForRow(dynamicDataId: String): List[DynamicDataAccessT] =
    DynamicDataAccess.findAll(By(DynamicDataAccess.DynamicDataId, dynamicDataId))

  override def getReadableDynamicDataIds(userId: String, entityName: String, bankId: Option[String]): List[String] = {
    val base: List[QueryParam[DynamicDataAccess]] = List(
      By(DynamicDataAccess.UserId, userId),
      By(DynamicDataAccess.EntityName, entityName),
      By(DynamicDataAccess.CanRead, true)
    )
    val scoped = bankId match {
      case Some(b) => By(DynamicDataAccess.BankId, b) :: base
      case None    => NullRef(DynamicDataAccess.BankId) :: base
    }
    DynamicDataAccess.findAll(scoped: _*).map(_.DynamicDataId.get)
  }

  override def allows(dynamicDataId: String, userId: String, permission: DynamicDataAccessPermission): Boolean = {
    import DynamicDataAccessPermission._
    DynamicDataAccess.find(
      By(DynamicDataAccess.DynamicDataId, dynamicDataId),
      By(DynamicDataAccess.UserId, userId)
    ).map { row =>
      permission match {
        case Read   => row.CanRead.get
        case Update => row.CanUpdate.get
        case Delete => row.CanDelete.get
        case Grant  => row.CanGrant.get
      }
    }.getOrElse(false)
  }

  override def deleteAllForRow(dynamicDataId: String): Box[Boolean] = tryo {
    DynamicDataAccess.findAll(By(DynamicDataAccess.DynamicDataId, dynamicDataId)).forall(_.delete_!)
  }

  override def deleteAllForEntity(entityName: String, bankId: Option[String]): Box[Boolean] = tryo {
    val params: List[QueryParam[DynamicDataAccess]] = bankId match {
      case Some(b) => List(By(DynamicDataAccess.EntityName, entityName), By(DynamicDataAccess.BankId, b))
      case None    => List(By(DynamicDataAccess.EntityName, entityName), NullRef(DynamicDataAccess.BankId))
    }
    DynamicDataAccess.findAll(params: _*).forall(_.delete_!)
  }
}

class DynamicDataAccess extends DynamicDataAccessT with LongKeyedMapper[DynamicDataAccess] with IdPK {

  override def getSingleton = DynamicDataAccess

  object DynamicDataId extends MappedString(this, 255)
  object UserId extends MappedString(this, 255)
  object CanRead extends MappedBoolean(this)
  object CanUpdate extends MappedBoolean(this)
  object CanDelete extends MappedBoolean(this)
  object CanGrant extends MappedBoolean(this)
  object GrantedBy extends MappedString(this, 255)
  object EntityName extends MappedString(this, 255)
  object BankId extends MappedString(this, 255)

  override def dynamicDataId: String = DynamicDataId.get
  override def userId: String = UserId.get
  override def canRead: Boolean = CanRead.get
  override def canUpdate: Boolean = CanUpdate.get
  override def canDelete: Boolean = CanDelete.get
  override def canGrant: Boolean = CanGrant.get
  override def grantedBy: String = GrantedBy.get
  override def entityName: String = EntityName.get
  override def bankId: Option[String] = Option(BankId.get)
}

object DynamicDataAccess extends DynamicDataAccess with LongKeyedMetaMapper[DynamicDataAccess] {
  override def dbIndexes =
    UniqueIndex(DynamicDataId, UserId) ::
    Index(UserId, EntityName, BankId) ::
    Index(DynamicDataId, GrantedBy) ::
    super.dbIndexes
}
