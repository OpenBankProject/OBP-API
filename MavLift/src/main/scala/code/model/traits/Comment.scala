package code.model.traits
import java.util.Date
import net.liftweb.common.Box

trait Comment {
  // The person that posted the comment
  def postedBy : Box[User] 
  
  // The actual text of the comment
  def text : String
  
  def datePosted : Date
}