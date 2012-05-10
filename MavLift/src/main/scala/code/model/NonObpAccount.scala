package code.model

trait NonObpAccount {
  
  def id : String
  
  val publicAlias : String
  val privateAlias : String
  val accountHolderName : String
  val moreInfo : String
  val url : String
  val imageUrl : String
  val openCorporatesUrl : String

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
    }
  }*/
  
  class Alias
  class Public extends Alias
  class Private extends Alias
  class None extends Alias
  
  case class AccountName(display : String, aliasType : Alias)
  
  /*def moreInfo(view: View) : Option[String] = if(view.canSeeMoreInfo) Some(moreInfo) else None
  def url(view: View) : Option[String] = if(view.canSeeUrl) Some(url) else None
  def imageUrl(view: View) : Option[String] = if(view.canSeeImageUrl) Some(imageUrl) else None
  def openCorporatesUrl(view: View) : Option[String] = if(view.canSeeOpenCorporatesUrl) Some(openCorporatesUrl) else None*/
  
}