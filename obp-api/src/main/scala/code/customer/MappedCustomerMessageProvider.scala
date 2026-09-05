package code.customer

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.{BankId, Customer, CustomerMessage, User}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * A message shown to a customer.
 *
 * The table has TWO owner columns and they are not interchangeable: `user_c` (RESOURCEUSER's
 * numeric key) is written only by the deprecated addMessage path, `customer` (MAPPEDCUSTOMER's
 * numeric key) only by createCustomerMessage. Each read filters on exactly one of them, so a
 * message created one way is invisible to the other reader. That is why `user` carries a
 * deprecation note; the split is preserved rather than unified under a storage swap.
 */
case class MappedCustomerMessage(
  messageId: String,
  date: Date,
  fromPerson: String,
  fromDepartment: String,
  message: String,
  private val transportRaw: String
) extends CustomerMessage {
  override def transport: Option[String] =
    if (transportRaw == null || transportRaw.isEmpty) None else Some(transportRaw)
}

object MappedCustomerMessage {

  private val selectColumns =
    fr"""SELECT mmessageid, createdat, mfromperson, mfromdepartment, mmessage, mtransport
         FROM mappedcustomermessage"""

  private type Row = (Option[String], Option[java.sql.Timestamp], Option[String], Option[String],
    Option[String], Option[String])

  private def fromRow(row: Row): MappedCustomerMessage = row match {
    case (messageId, createdAt, fromPerson, fromDepartment, message, transport) =>
      MappedCustomerMessage(messageId.orNull, createdAt.orNull, fromPerson.orNull,
        fromDepartment.orNull, message.orNull, transport.orNull)
  }

  private def query(condition: Fragment): List[MappedCustomerMessage] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAllByUserKeyAndBank(userKey: Long, bankId: String): List[MappedCustomerMessage] =
    query(fr"WHERE user_c = $userKey AND bank = $bankId ORDER BY updatedat DESC, id DESC")

  def findAllByCustomerKeyAndBank(customerKey: Long, bankId: String): List[MappedCustomerMessage] =
    query(fr"WHERE customer = $customerKey AND bank = $bankId ORDER BY updatedat DESC, id DESC")

  private def insert(userKey: Option[Long], customerKey: Option[Long], bankId: String,
                     message: String, fromDepartment: String, fromPerson: String,
                     transport: String): MappedCustomerMessage = {
    val messageId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcustomermessage
            (mmessageid, user_c, customer, bank, mmessage, mfromdepartment, mfromperson, mtransport,
             createdat, updatedat)
            VALUES ($messageId, $userKey, $customerKey, $bankId, $message, $fromDepartment,
             $fromPerson, $transport, $now, $now)"""
        .update.run)
    MappedCustomerMessage(messageId, now, fromPerson, fromDepartment, message, transport)
  }

  def insertForUser(userKey: Long, bankId: String, message: String, fromDepartment: String,
                    fromPerson: String): MappedCustomerMessage =
    insert(Some(userKey), None, bankId, message, fromDepartment, fromPerson, "")

  def insertForCustomer(customerKey: Long, bankId: String, transport: String, message: String,
                        fromDepartment: String, fromPerson: String): MappedCustomerMessage =
    insert(None, Some(customerKey), bankId, message, fromDepartment, fromPerson, transport)

  def count(): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM mappedcustomermessage".query[Long].unique)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomermessage".update.run)
    ()
  }
}

object MappedCustomerMessageProvider extends CustomerMessageProvider {

  override def getMessages(user: User, bankId: BankId): List[CustomerMessage] =
    MappedCustomerMessage.findAllByUserKeyAndBank(user.userPrimaryKey.value, bankId.value)

  override def addMessage(user: User, bankId: BankId, message: String, fromDepartment: String,
                          fromPerson: String): MappedCustomerMessage =
    MappedCustomerMessage.insertForUser(user.userPrimaryKey.value, bankId.value, message,
      fromDepartment, fromPerson)

  override def createCustomerMessage(customer: Customer, bankId: BankId, transport: String,
                                     message: String, fromDepartment: String,
                                     fromPerson: String): MappedCustomerMessage = {
    val mappedCustomer = MappedCustomer.findByCustomerId(customer.customerId).openOrThrowException(
      "the customer a message is being created for must exist")
    MappedCustomerMessage.insertForCustomer(mappedCustomer.customerPrimaryKey, bankId.value,
      transport, message, fromDepartment, fromPerson)
  }

  override def getCustomerMessages(customer: Customer, bankId: BankId): List[CustomerMessage] = {
    val mappedCustomer = MappedCustomer.findByCustomerId(customer.customerId).openOrThrowException(
      "the customer whose messages are being read must exist")
    MappedCustomerMessage.findAllByCustomerKeyAndBank(mappedCustomer.customerPrimaryKey, bankId.value)
  }
}
