package code.model
import code.model.AliasType

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

class FilteredNonObpAccount(id : Option[String], accountHolderName : Option[String], alias : AliasType,
    moreInfo : Option[String], url : Option[String], imageurl : Option[String], openCorporatesUrl : Option[String]) {
}