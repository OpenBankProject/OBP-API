package code.accountholders

import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util.Helper.MdcLoggable
import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import net.liftweb.common._
import net.liftweb.mapper._
import net.liftweb.common.Box


/**
  * the link userId <--> bankId + accountId 
  */
class MapperAccountHolders extends LongKeyedMapper[MapperAccountHolders] with IdPK {

  def getSingleton = MapperAccountHolders

  object user extends MappedLongForeignKey(this, ResourceUser)

  object accountBankPermalink extends UUIDString(this)
  object accountPermalink extends AccountIdString(this)
  object source extends MappedString(this, 255)

}


object MapperAccountHolders extends MapperAccountHolders with AccountHolders with LongKeyedMetaMapper[MapperAccountHolders] with MdcLoggable  {

  // NOTE: !!! Uses a DIFFERENT TABLE NAME PREFIX TO ALL OTHERS i.e. MAPPER not MAPPED !!!!!

  override def dbIndexes = Index(accountBankPermalink, accountPermalink) :: Nil

  //Note, this method, will not check the existing of bankAccount, any value of BankIdAccountId
  //Can create the MapperAccountHolders.
  def getOrCreateAccountHolder(user: User, bankIdAccountId :BankIdAccountId, source: Option[String] = None): Box[MapperAccountHolders] ={
  
    val mapperAccountHolder = MapperAccountHolders.find(
      By(MapperAccountHolders.user, user.userPrimaryKey.value),
      By(MapperAccountHolders.accountBankPermalink, bankIdAccountId.bankId.value),
      By(MapperAccountHolders.accountPermalink, bankIdAccountId.accountId.value)
    )
  
    mapperAccountHolder match {
      case Full(vImpl) => {
        logger.debug(
          s"getOrCreateAccountHolder --> the accountHolder has been existing in server !"
        )
        mapperAccountHolder
      }
      case Empty => {
        val holder: MapperAccountHolders = MapperAccountHolders.create
          .accountBankPermalink(bankIdAccountId.bankId.value)
          .accountPermalink(bankIdAccountId.accountId.value)
          .user(user.userPrimaryKey.value)
          .source(source.getOrElse(null))
          .saveMe
        logger.debug(
          s"getOrCreateAccountHolder--> create account holder: $holder"
        )
        Full(holder)
      }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
      
  }
  

  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    val accountHolders = MapperAccountHolders.findAll(
      By(MapperAccountHolders.accountBankPermalink, bankId.value),
      By(MapperAccountHolders.accountPermalink, accountId.value),
      PreCache(MapperAccountHolders.user)
    )

    //accountHolders --> user
    accountHolders.flatMap { accHolder =>
      ResourceUser.find(By(ResourceUser.id, accHolder.user.get))
    }.toSet
  }
  
  def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId] = {
    val accountHolders = MapperAccountHolders.findAll(
      By(MapperAccountHolders.accountBankPermalink, bankId.value),
      By(MapperAccountHolders.user, user.asInstanceOf[ResourceUser])
    )
    transformHolderToAccount(accountHolders)
  }

  def getAccountsHeldByUser(user: User, source: Option[String] = None): Set[BankIdAccountId] = {
      val accountHolders = if(source.isEmpty){
        MapperAccountHolders.findAll(By(MapperAccountHolders.user, user.asInstanceOf[ResourceUser]))
      }else if (source.equals(Some("")) || source.equals(Some(null))){
        MapperAccountHolders.findAll(
          By(MapperAccountHolders.user, user.asInstanceOf[ResourceUser]),
          NullRef(MapperAccountHolders.source)
        )
      }else{
        MapperAccountHolders.findAll(
          By(MapperAccountHolders.user, user.asInstanceOf[ResourceUser]),
          By(MapperAccountHolders.source, source.get)
        )
      }
      transformHolderToAccount(accountHolders)
    }

  private def transformHolderToAccount(accountHolders: List[MapperAccountHolders]) = {
    //accountHolders --> BankIdAccountIds
    accountHolders.map { accHolder =>
      BankIdAccountId(BankId(accHolder.accountBankPermalink.get), AccountId(accHolder.accountPermalink.get))
    }.toSet
  }

  def bulkDeleteAllAccountHolders(): Box[Boolean] = {
    Full( MapperAccountHolders.bulkDelete_!!() )
  }

  def deleteAccountHolder(user: User, bankIdAccountId :BankIdAccountId): Box[Boolean] = {
    MapperAccountHolders.find(
      By(MapperAccountHolders.user, user.userPrimaryKey.value),
      By(MapperAccountHolders.accountBankPermalink, bankIdAccountId.bankId.value),
      By(MapperAccountHolders.accountPermalink, bankIdAccountId.accountId.value)
    ).map(_.delete_!)
  }
  

}
