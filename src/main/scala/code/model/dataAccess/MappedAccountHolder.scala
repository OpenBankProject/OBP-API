package code.model.dataAccess

import net.liftweb.mapper._
import net.liftweb.common._

class MappedAccountHolder extends LongKeyedMapper[MappedAccountHolder] with IdPK {

  def getSingleton = MappedAccountHolder

  object user extends MappedLongForeignKey(this, APIUser)

  object accountBankPermalink extends MappedString(this, 255)
  object accountPermalink extends MappedString(this, 255)

}

object MappedAccountHolder extends MappedAccountHolder with LongKeyedMetaMapper[MappedAccountHolder] {

  private val logger = Logger(classOf[MappedAccountHolder])

  override def dbIndexes = Index(accountBankPermalink, accountPermalink) :: Nil

  def createMappedAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean = {
    val holder = MappedAccountHolder.create
      .accountBankPermalink(bankId)
      .accountPermalink(accountId)
      .user(userId)
      .saveMe
    if(source != "MappedAccountHolder") logger.info(s"------------> created mappedUserHolder ${holder} at ${source}")
    if(holder.saved_?) true else false
  }
}
