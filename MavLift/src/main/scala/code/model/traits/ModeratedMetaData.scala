package code.model.traits

class ModeratedMetaData(filteredId : Option[String], filteredAccountHolderName : Option[String], filteredAlias : AliasType,
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