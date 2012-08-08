package code.model.implementedTraits

import code.model.traits.{MetaData, Comment, OtherBankAccountMetadata, TransactionMetadata}
import code.model.dataAccess.{OtherAccount, OBPComment}
import net.liftweb.common.Loggable

class OtherBankAccountMetadataImpl(oAcc : OtherAccount) extends OtherBankAccountMetadata {

   def publicAlias : String = oAcc.publicAlias.get
   def privateAlias : String = oAcc.privateAlias.get
   def moreInfo : String = oAcc.moreInfo.get
   def url : String = oAcc.url.get
   def imageUrl : String = oAcc.imageUrl.get
   def openCorporatesUrl : String = oAcc.openCorporatesUrl.get
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

