package code.context

trait UserAuthContextRequest {
  def userAuthContextRequestId : String 
  def userId : String 
  def key : String
  def value : String
  def challenge: String
  def status: String
}

object ConsentRequestStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED = Value
}