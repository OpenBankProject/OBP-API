package code.model

trait NonObpAccount {
  
  def id : String
  
  def accountName(view: View) = view.accountName(this)
  def isAlias(view: View) = view.isAlias(this)
  def moreInfo(view: View) = view.moreInfo(this)
  def url(view: View) = view.url(this)
  def imageUrl(view: View) = view.imageUrl(this)
  def openCorporatesUrl(view: View) = view.openCorporatesUrl(this)
  
}