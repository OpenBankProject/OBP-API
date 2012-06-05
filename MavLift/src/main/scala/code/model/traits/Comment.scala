package code.model.traits
import java.util.Date

trait Comment {

  // The person that posted the comment
  def postedBy : Option[User] 
  
  // The actual text of the comment
  def text : String
  
  //TODO: Remove Option once comment dates are implemented
  def datePosted : Option[Date]
  
}