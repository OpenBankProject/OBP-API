package code.model

trait View {
  
  //e.g. "Anonymous", "Authorities", "Our Network", etc.
  def name : String
  
  protected val generatedAlias : String
  protected def manualAlias : Option[String]
  protected val realAccountName : String
  
  def accountName(nObpAcc: NonObpAccount) : String = {
    if(isAlias(nObpAcc)) manualAlias getOrElse generatedAlias
    else realAccountName
  }
  
  def isAlias(nObpAcc: NonObpAccount) : Boolean
  def moreInfo(nObpAcc: NonObpAccount) : String
  def url(nObpAcc: NonObpAccount) : String
  def imageUrl(nObpAcc: NonObpAccount) : String
  def openCorporatesUrl(nObpAcc: NonObpAccount) : String
  
}