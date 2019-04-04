package code.context

trait UserAuthContextUpdate {
  def userAuthContextUpdateId : String 
  def userId : String 
  def key : String
  def value : String
  def challenge: String
  def status: String
}

object UserAuthContextUpdateStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED = Value
}