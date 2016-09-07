package code.socialmedia

import java.util.Date
import net.liftweb.util.SimpleInjector


// TODO Rename to SocialMediaHandle
object SocialMediaHandle extends SimpleInjector {

  val socialMediaHandleProvider = new Inject(buildOne _) {}

  def buildOne: SocialMediaHandleProvider = MappedSocialMediasProvider

}


// TODO Rename to SocialMediaHandlesProvider etc.
trait SocialMediaHandleProvider {

  def getSocialMedias(customerNumber: String) : List[SocialMedia]

  def addSocialMedias(customerNumber: String, `type`: String, handle: String, dateAdded: Date, dateActivated: Date) : Boolean

}


// TODO Rename to SocialMediaHandle
trait SocialMedia {
  def customerNumber : String
  def `type` : String
  def handle : String
  def dateAdded : Date
  def dateActivated : Date
}