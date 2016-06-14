package code.entitlement


import code.util.{MappedUUID, DefaultStringField}
import net.liftweb.mapper._
import net.liftweb.common.Box

object MappedEntitlementsProvider extends EntitlementProvider {

  override def getEntitlement(bankId: String, userId: String, roleName: String): Box[MappedEntitlement] = {
    // Return a Box so we can handle errors later.
    MappedEntitlement.find(
      By(MappedEntitlement.mBankId, bankId),
      By(MappedEntitlement.mUserId, userId),
      By(MappedEntitlement.mRoleName, roleName)
      )
  }

  override def getEntitlements(userId: String): Box[List[MappedEntitlement]] = {
    // Return a Box so we can handle errors later.
    Some(MappedEntitlement.findAll(
      By(MappedEntitlement.mUserId, userId),
      OrderBy(MappedEntitlement.updatedAt, Descending)))
  }

  override def getEntitlements: Box[List[MappedEntitlement]] = {
    // Return a Box so we can handle errors later.
    Some(MappedEntitlement.findAll(OrderBy(MappedEntitlement.updatedAt, Descending)))
  }


  override def addEntitlement(bankId: String, userId: String, roleName: String): Box[MappedEntitlement] = {
    // Return a Box so we can handle errors later.
    val addEntitlement = MappedEntitlement.create
      .mBankId(bankId)
      .mUserId(userId)
      .mRoleName(roleName)
      .saveMe()
    Some(addEntitlement)
  }
}

class MappedEntitlement extends Entitlement
with LongKeyedMapper[MappedEntitlement] with IdPK with CreatedUpdated {

  def getSingleton = MappedEntitlement

  object mEntitlementId extends MappedUUID(this)
  object mBankId extends DefaultStringField(this)
  object mUserId extends DefaultStringField(this)
  object mRoleName extends DefaultStringField(this)

  override def entitlementId: String = mEntitlementId.get.toString
  override def bankId: String = mBankId.get
  override def userId: String = mUserId.get
  override def roleName: String = mRoleName.get

}

object MappedEntitlement extends MappedEntitlement with LongKeyedMetaMapper[MappedEntitlement] {
  override def dbIndexes = UniqueIndex(mEntitlementId) :: super.dbIndexes
}