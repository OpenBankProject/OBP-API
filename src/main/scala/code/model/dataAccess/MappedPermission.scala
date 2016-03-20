package code.model.dataAccess

import net.liftweb.mapper._


/*
 * Simple record for storing permissions.
 */
object MappedPermission extends MappedPermission with LongKeyedMetaMapper[MappedPermission]  {

  def createUserPermission(uid: Long, aPerm: APermission) = {
    create.userId(uid).permission(aPerm.toString)
  }

  def removeAllUserPermissions(uid: Long) = {
    MappedPermission.findAll(By(userId, uid)).map(_.delete_!)
  }

  def toAPermission(perm: MappedPermission) = APermission.fromString(perm.permission.get)
  def fromAPermission(aPerm: APermission): MappedPermission = MappedPermission.create.permission(aPerm.toString)

  def userPermissions(uid: Long): List[APermission] = MappedPermission.findAll(By(userId, uid)).map(toAPermission)

}

class MappedPermission extends LongKeyedMapper[MappedPermission] with IdPK {
  def getSingleton = MappedPermission

  /**
    * This field is empty for permissions attached directly to the user
    */
  object roleId extends MappedStringForeignKey(this, Role, 32) {
    def foreignMeta = Role
  }

  /**
    * This field is empty for permissions attached to a role
    */
  object userId extends MappedLong(this) {
    override def dbIndexed_? = true
  }

  object permission extends MappedString(this, 1024)
}

