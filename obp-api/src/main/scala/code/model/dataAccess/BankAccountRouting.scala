package code.model.dataAccess

import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model.{AccountId => ModelAccountId, BankId => ModelBankId, _}
import net.liftweb.mapper._

class BankAccountRouting extends BankAccountRoutingTrait with LongKeyedMapper[BankAccountRouting] with IdPK with CreatedUpdated {
  def getSingleton: BankAccountRouting.type = BankAccountRouting

  override def bankId: ModelBankId = ModelBankId(BankId.get)

  override def accountId: ModelAccountId = ModelAccountId(AccountId.get)

  override def accountRouting: AccountRouting = AccountRouting(AccountRoutingScheme.get, AccountRoutingAddress.get)

  object BankId extends UUIDString(this)

  object AccountId extends AccountIdString(this)

  object AccountRoutingScheme extends MappedString(this, 32)

  object AccountRoutingAddress extends MappedString(this, 128)

}

object BankAccountRouting extends BankAccountRouting with LongKeyedMetaMapper[BankAccountRouting] {

  override def dbIndexes: List[BaseIndex[BankAccountRouting]] =
    UniqueIndex(BankId, AccountId, AccountRoutingScheme) :: UniqueIndex(BankId, AccountRoutingScheme, AccountRoutingAddress) :: super.dbIndexes

}

