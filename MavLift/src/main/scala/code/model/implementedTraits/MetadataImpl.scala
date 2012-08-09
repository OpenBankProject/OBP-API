package code.model.implementedTraits

import code.model.traits.{MetaData, Comment, OtherBankAccountMetadata, TransactionMetadata}
import code.model.dataAccess.{OtherAccount, OBPComment}
import net.liftweb.common.Loggable

class OtherBankAccountMetadataImpl(_publicAlias : String, _privateAlias : String,_moreInfo : String,
_url : String, _imageUrl : String, _openCorporatesUrl : String) extends OtherBankAccountMetadata {

   def publicAlias : String = _publicAlias
   def privateAlias : String = _privateAlias
   def moreInfo : String = _moreInfo
   def url : String = _url
   def imageUrl : String = _imageUrl
   def openCorporatesUrl : String = _openCorporatesUrl
}
//comment => env.narrative(comment).save
class TransactionMetadataImpl(narative : String, comments_ : List[Comment], 
  saveOwnerComment : String => Unit, addCommentFunc : (String, String) => Unit ) extends TransactionMetadata with Loggable
{
  def ownerComment = if(! narative.isEmpty) Some(narative) else None
  def ownerComment(comment : String) = saveOwnerComment(comment)
  def comments : List[Comment] = comments_
  def addComment(comment : Comment) : Unit =
  { 
    val emailAddress = for{
      poster <- comment.postedBy
    } yield poster.emailAddress
    
    emailAddress match{
      case Some(emailAdr) => addCommentFunc(emailAdr, comment.text)
      case _ => logger.warn("A comment was not added due to a lack of valid email address")
    }
  }
}

