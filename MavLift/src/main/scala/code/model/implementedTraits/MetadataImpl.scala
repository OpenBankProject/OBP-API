package code.model.implementedTraits

import code.model.traits.{Comment, OtherBankAccountMetadata, TransactionMetadata}
import code.model.dataAccess.{OtherAccount, OBPComment}
import net.liftweb.common.Loggable
import java.util.Date

class OtherBankAccountMetadataImpl(_publicAlias : String, _privateAlias : String,_moreInfo : String,
_url : String, _imageUrl : String, _openCorporatesUrl : String) extends OtherBankAccountMetadata {

   def publicAlias : String = _publicAlias
   def privateAlias : String = _privateAlias
   def moreInfo : String = _moreInfo
   def url : String = _url
   def imageUrl : String = _imageUrl
   def openCorporatesUrl : String = _openCorporatesUrl
}
class TransactionMetadataImpl(narative : String, comments_ : List[Comment], 
  saveOwnerComment : String => Unit, addCommentFunc : (Long, String, Date) => Unit ) 
  extends TransactionMetadata with Loggable
{
  def ownerComment = if(! narative.isEmpty) Some(narative) else None
  def ownerComment(comment : String) = saveOwnerComment(comment)
  def comments : List[Comment] = comments_
  def addComment(userId: Long, text: String, datePosted : Date) : Unit = 
  {
    println("--> received values : " + userId + " "+ text + " " + datePosted)
    addCommentFunc(userId, text, datePosted)
  }  
}

