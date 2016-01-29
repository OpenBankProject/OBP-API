package code.socialmedia

import java.util.Date
import net.liftweb.util.SimpleInjector


object SocialMedias extends SimpleInjector {

  val socialMediaProvider = new Inject(buildOne _) {}

  def buildOne: SocialMediaProvider = MappedSocialMediasProvider

}

trait SocialMediaProvider {

  def getSocialMedias(customerNumber: String) : List[SocialMedia]

  def addSocialMedias(customerNumber: String, `type`: String, handle: String, dateAdded: Date, dateActivated: Date) : Boolean

}

trait SocialMedia {
  def customerNumber : String
  def `type` : String
  def handle : String
  def dateAdded : Date
  def dateActivated : Date
}