package code.accountholder

import code.model.{AccountId, BankId, User}
import code.model.dataAccess.ResourceUser
import code.users.Users
import net.liftweb.common._
import net.liftweb.mapper._


class MapperAccountHolders extends LongKeyedMapper[MapperAccountHolders] with IdPK {

  def getSingleton = MapperAccountHolders

  object user extends MappedLongForeignKey(this, ResourceUser)

  object accountBankPermalink extends MappedString(this, 255)
  object accountPermalink extends MappedString(this, 255)

}


object MapperAccountHolders extends MapperAccountHolders with AccountHolders with LongKeyedMetaMapper[MapperAccountHolders] {

  private val logger = Logger(classOf[AccountHolders])

  override def dbIndexes = Index(accountBankPermalink, accountPermalink) :: Nil

  def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean = {
    val holder = MapperAccountHolders.create
      .accountBankPermalink(bankId)
      .accountPermalink(accountId)
      .user(userId)
      .saveMe
    if(source != "MappedAccountHolder") logger.info(s"------------> created mappedUserHolder ${holder} at ${source}")
    if(holder.saved_?)
      true
    else
      false
  }

  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    val results = MapperAccountHolders.findAll(
      By(MapperAccountHolders.accountBankPermalink, bankId.value),
      By(MapperAccountHolders.accountPermalink, accountId.value))

    results.flatMap { accHolder =>
      ResourceUser.find(By(ResourceUser.id, accHolder.user))
    }.toSet
  }

  def bulkDeleteAllAccountHolders(): Boolean = {
    MapperAccountHolders.bulkDelete_!!()
  }

}
