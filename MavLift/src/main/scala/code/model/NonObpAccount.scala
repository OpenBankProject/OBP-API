package code.model

trait NonObpAccount {
  
  def id : String
  
  def publicAlias : String
  def privateAlias : String
  def accountHolderName : String
  def moreInfo : String
  def url : String
  def imageUrl : String
  def openCorporatesUrl : String

  /**
   * Current rules: 
   * 
   * If anonymous, and a public alias exists : Show the public alias
   * If anonymous, and no public alias exists : Show the real account holder
   * If our network, and a private alias exists : Show the private alias
   * If our network, and no private alias exists : Show the real account holder
   */
  /*def accountHolderName(view: View) : AccountName = {
    if(view.usePublicAliasIfOneExists){
      if(publicAlias != "") AccountName(publicAlias, true)
      else  AccountName(accountHolderName, false)
    } else if (view.usePrivateAliasIfOneExists){
      if(privateAlias != "") AccountName(privateAlias, true)
      else AccountName(accountHolderName, false)
    } else {
      AccountName(accountHolderName, false)
    }un 
  }*/
  
  
  /*def moreInfo(view: View) : Option[String] = if(view.canSeeMoreInfo) Some(moreInfo) else None
  def url(view: View) : Option[String] = if(view.canSeeUrl) Some(url) else None
  def imageUrl(view: View) : Option[String] = if(view.canSeeImageUrl) Some(imageUrl) else None
  def openCorporatesUrl(view: View) : Option[String] = if(view.canSeeOpenCorporatesUrl) Some(openCorporatesUrl) else None*/
  
}
//
//class nonObpAccountImp(account : OtherAccount) extends NonObpAccount
//{
//	def id = ""
//    def publicAlias = account.publicAlias.get.toString()
//	def privateAlias = account.privateAlias.get.toString()
//	def accountHolderName : String
//	def moreInfo = account.moreInfo.get.toString()
//	def url = account.url.toString()
//	def imageUrl = account.imageUrl.toString()
//	def openCorporatesUrl = account.openCorporatesUrl.get.toString()
//}



































