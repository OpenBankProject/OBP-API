package code.customer

import java.util.Date

import code.model.{BankId, User}
import code.model.dataAccess.ResourceUser
import code.util.{DefaultStringField, MappedUUID}
import net.liftweb.mapper._

object MappedCustomerMessageProvider extends CustomerMessageProvider {

  override def getMessages(user: User, bankId : BankId): List[CustomerMessage] = {
    MappedCustomerMessage.findAll(
      By(MappedCustomerMessage.user, user.resourceUserId.value),
      By(MappedCustomerMessage.bank, bankId.value),
      OrderBy(MappedCustomerMessage.updatedAt, Descending))
  }


  override def addMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String): Boolean = {
    MappedCustomerMessage.create
      .mFromDepartment(fromDepartment)
      .mFromPerson(fromPerson)
      .mMessage(message)
      .user(user.resourceUserId.value)
      .bank(bankId.value).save()
  }
}

class MappedCustomerMessage extends CustomerMessage
  with LongKeyedMapper[MappedCustomerMessage] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerMessage

  object user extends MappedLongForeignKey(this, ResourceUser)
  object bank extends DefaultStringField(this)

  object mFromPerson extends DefaultStringField(this)
  object mFromDepartment extends DefaultStringField(this)
  object mMessage extends DefaultStringField(this)
  object mMessageId extends MappedUUID(this)


  override def messageId: String = mMessageId.get
  override def date: Date = createdAt.get
  override def fromPerson: String = mFromPerson.get
  override def fromDepartment: String = mFromDepartment.get
  override def message: String = mMessage.get
}

object MappedCustomerMessage extends MappedCustomerMessage with LongKeyedMetaMapper[MappedCustomerMessage] {
  override def dbIndexes = UniqueIndex(mMessageId) :: Index(user, bank, updatedAt) :: super.dbIndexes
}