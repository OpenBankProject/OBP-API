package code.model


class NonObpAccountImpl(oAcc : OtherAccount) extends NonObpAccount {

  def id(): String = { 
    ""
  }

   val publicAlias : String = oAcc.publicAlias.get
   val privateAlias : String = oAcc.privateAlias.get
   val accountHolderName : String = oAcc.holder.get
   val moreInfo : String = oAcc.moreInfo.get
   val url : String = oAcc.url.get
   val imageUrl : String = oAcc.imageUrl.get
   val openCorporatesUrl : String = oAcc.openCorporatesUrl.get
}

class FilteredNonObpAccount(filteredId : Option[String], filteredAccountHolderName : Option[String], filteredAlias : AliasType,
    filteredMoreInfo : Option[String], filteredUrl : Option[String], filteredImageUrl : Option[String], filteredOpenCorporatesUrl : Option[String]) {
  
  def alias = filteredAlias
  def accountHolderName = filteredAccountHolderName
  def moreInfo = filteredMoreInfo match{
    case Some(o) => o
    case _ => None
  }
  def url = filteredUrl
  def imageUrl = filteredImageUrl
  def openCorporatesUrl = filteredOpenCorporatesUrl
  
  def isAlias = alias match{
    case Public | Private => true
    case _ => false
  }
}