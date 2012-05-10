package code.model

import java.util.Date
import net.liftweb.mapper.By

class CommentImpl(comment: OBPComment) extends Comment {

  def postedBy: Option[OBPUser] = { 
    val email = comment.email.get
    User.find(By(User.email, email))
  }

  def text: String = { 
    comment.text.get
  }

  def datePosted: Option[Date]= { 
    None
  }
  
}