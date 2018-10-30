package code.context

trait UserAuthContext {
  def userAuthContextId : String 
  def userId : String 
  def key : String
  def value : String
}