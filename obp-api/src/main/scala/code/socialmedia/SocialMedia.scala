/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.socialmedia

import java.util.Date
import net.liftweb.util.SimpleInjector


// TODO Rename to SocialMediaHandle
object SocialMediaHandle extends SimpleInjector {

  val socialMediaHandleProvider = new Inject(() => buildOne) {}

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