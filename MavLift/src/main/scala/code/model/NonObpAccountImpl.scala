package code.model


class NonObpAccountImpl(oAcc : OtherAccount) extends NonObpAccount {

  def id(): String = { 
    ""
  }

   def publicAlias : String = oAcc.publicAlias.get
   def privateAlias : String = oAcc.privateAlias.get
   def accountHolderName : String = oAcc.holder.get
   def moreInfo : String = oAcc.moreInfo.get
   def url : String = oAcc.url.get
   def imageUrl : String = oAcc.imageUrl.get
   def openCorporatesUrl : String = oAcc.openCorporatesUrl.get
}
class FilteredNonObpAccount(filteredId : Option[String], filteredAccountHolderName : Option[String], filteredAlias : AliasType,
    filteredMoreInfo : Option[String], filteredUrl : Option[String], filteredImageUrl : Option[String], filteredOpenCorporatesUrl : Option[String]) {
  
  def alias = filteredAlias
  def accountHolderName = filteredAccountHolderName
  def moreInfo = filteredMoreInfo 
  def url = filteredUrl
  def imageUrl = filteredImageUrl
  def openCorporatesUrl = filteredOpenCorporatesUrl
  
  def isAlias = alias match{
    case Public | Private => true
    case _ => false
  }
}

//class NonObpAccountImpl(id_ : String, publicAlias_ : String, privateAlias_ : String, accountHolderName_ : String, moreInfo_ : String, url_ : String, imageUrl_ : String, openCorporatesUrl_ : String ) extends NonObpAccount {
//  
//  def id(): String = { 
//    ""
//  }
//
//   def publicAlias = publicAlias_;
//   def privateAlias = privateAlias_;
//   def accountHolderName = accountHolderName_;
//   def moreInfo = moreInfo_;
//   def url  = url_;
//   def imageUrl = imageUrl_;
//   def openCorporatesUrl = openCorporatesUrl_;
//}