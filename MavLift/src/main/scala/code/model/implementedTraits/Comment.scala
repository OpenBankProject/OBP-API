package code.model.implementedTraits

import java.util.Date
import net.liftweb.mapper.By
import code.model.traits.Comment
import code.model.dataAccess.{OBPComment,OBPUser}
import code.model.traits.User

class CommentImpl(comment: OBPComment) extends Comment {

  def postedBy: Option[User] = { 
    val email = comment.email.get
    OBPUser.find(By(OBPUser.email, email))
  }

  def text: String = { 
    comment.text.get
  }

  def datePosted: Option[Date]= { 
    None
  }
  
}