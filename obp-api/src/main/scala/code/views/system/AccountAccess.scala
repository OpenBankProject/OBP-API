package code.views.system

import code.api.Constant.ALL_CONSUMERS
import code.model.dataAccess.ResourceUser
import code.util.UUIDString
import net.liftweb.mapper._
/*
This stores the link between A User and a View
A User can't use a View unless it is listed here.
 */
class AccountAccess extends LongKeyedMapper[AccountAccess] with IdPK with CreatedUpdated {
  def getSingleton = AccountAccess
  object user_fk extends MappedLongForeignKey(this, ResourceUser)
  object bank_id extends MappedString(this, 255)
  
  //If consumer_id is `ALL-CONSUMERS`, any consumers can use this record
  //If consumer_id is consumerId (obp UUID), only same consumer can use this record
  object consumer_id extends MappedString(this, 255){
    override def defaultValue = ALL_CONSUMERS
  }
  object account_id extends MappedString(this, 255)
  object view_id extends UUIDString(this)
  object view_fk extends MappedLongForeignKey(this, ViewDefinition)
}
object AccountAccess extends AccountAccess with LongKeyedMetaMapper[AccountAccess] {
  override def dbIndexes: List[BaseIndex[AccountAccess]] = UniqueIndex(bank_id, account_id, view_fk, user_fk, consumer_id) :: super.dbIndexes
}
