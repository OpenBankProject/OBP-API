package code.model.traits

trait User {
  
  def emailAddress : String
  def userName : String
  def permittedViews(bankAccountPermalink : String) : Set[View]
  
}