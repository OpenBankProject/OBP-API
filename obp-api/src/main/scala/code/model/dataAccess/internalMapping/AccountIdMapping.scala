package code.model.dataAccess.internalMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.{BankId, AccountId}
import net.liftweb.mapper._

class AccountIdMapping extends AccountIdMappingT with LongKeyedMapper[AccountIdMapping] with IdPK with CreatedUpdated {

  def getSingleton = AccountIdMapping

  object mAccountId extends MappedUUID(this)
  object mAccountPlainTextReference extends MappedString(this, 255)

  override def accountId = AccountId(mAccountId.get)
  override def accountPlainTextReference = mAccountPlainTextReference.get
  
}

object AccountIdMapping extends AccountIdMapping with LongKeyedMetaMapper[AccountIdMapping] {
  //one account info per bank for each api user
  override def dbIndexes = UniqueIndex(mAccountId) :: UniqueIndex(mAccountId, mAccountPlainTextReference) :: super.dbIndexes
}