package code.customer

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.ResourceUser
import code.util.{UUIDString, MappedUUID}
import net.liftweb.mapper._

object MappedCustomerMessageProvider extends CustomerMessageProvider {

  override def getMessages(user: User, bankId : BankId): List[CustomerMessage] = {
    MappedCustomerMessage.findAll(
      By(MappedCustomerMessage.user, user.userPrimaryKey.value),
      By(MappedCustomerMessage.bank, bankId.value),
      OrderBy(MappedCustomerMessage.updatedAt, Descending))
  }


  override def addMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String): Boolean = {
    MappedCustomerMessage.create
      .mFromDepartment(fromDepartment)
      .mFromPerson(fromPerson)
      .mMessage(message)
      .user(user.userPrimaryKey.value)
      .bank(bankId.value).save()
  }
}

class MappedCustomerMessage extends CustomerMessage
  with LongKeyedMapper[MappedCustomerMessage] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerMessage

  object user extends MappedLongForeignKey(this, ResourceUser)
  object bank extends UUIDString(this)

  object mFromPerson extends MappedString(this, 64)
  object mFromDepartment extends MappedString(this, 64)
  object mMessage extends MappedString(this, 1024)
  object mMessageId extends MappedUUID(this)


  override def messageId: String = mMessageId.get
  override def date: Date = createdAt.get
  override def fromPerson: String = mFromPerson.get
  override def fromDepartment: String = mFromDepartment.get
  override def message: String = mMessage.get
}

object MappedCustomerMessage extends MappedCustomerMessage with LongKeyedMetaMapper[MappedCustomerMessage] {
  override def dbIndexes = UniqueIndex(mMessageId) :: super.dbIndexes
}