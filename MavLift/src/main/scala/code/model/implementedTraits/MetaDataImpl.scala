package code.model.implementedTraits

import code.model.traits.MetaData
import code.model.dataAccess.OtherAccount

class MetaDataImpl(oAcc : OtherAccount) extends MetaData {

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

