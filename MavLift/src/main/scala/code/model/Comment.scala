package code.model
import java.util.Date

trait Comment {

  // The person that posted the comment
  def postedBy : OBPUser 
  
  // The actual text of the comment
  def text : String
  
  def datePosted : Date
  
}