package code.customer

import java.util.Date

import code.model.{BankId, User}
import net.liftweb.util.SimpleInjector


object CustomerMessages extends SimpleInjector {

  val customerMessageProvider = new Inject(buildOne _) {}

  def buildOne: CustomerMessageProvider = MappedCustomerMessageProvider

}

trait CustomerMessageProvider {

  //TODO: pagination? is this sorted by date?
  def getMessages(user : User, bankId : BankId) : List[CustomerMessage]

  def addMessage(user : User, bankId : BankId, message : String, fromDepartment : String, fromPerson : String) : Boolean

}

trait CustomerMessage {
  //TODO: message language?
  def messageId : String
  def date : Date
  def message : String
  def fromDepartment : String
  def fromPerson : String
}
