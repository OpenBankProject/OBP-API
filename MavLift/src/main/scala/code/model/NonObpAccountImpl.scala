package code.model

class NonObpAccountImpl(oAcc : OtherAccount) extends NonObpAccount {

  def id(): String = { 
    ""
  }

  protected val publicAlias : String = oAcc.publicAlias.get
  protected val privateAlias : String = oAcc.privateAlias.get
  protected val accountHolderName : String = oAcc.holder.get
  protected val moreInfo : String = oAcc.moreInfo.get
  protected val url : String = oAcc.url.get
  protected val imageUrl : String = oAcc.imageUrl.get
  protected val openCorporatesUrl : String = oAcc.openCorporatesUrl.get
}

class FilteredNonObpAccount() {
  
}