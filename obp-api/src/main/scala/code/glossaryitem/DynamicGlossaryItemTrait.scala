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

package code.glossaryitem

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object DynamicGlossaryItems extends SimpleInjector {
  val dynamicGlossaryItem = new Inject(() => buildOne) {}

  def buildOne: DynamicGlossaryItemProvider = MappedDynamicGlossaryItemProvider
}

/**
 * Dynamic Glossary Items are Glossary Items held in the database and maintained over the
 * /api/glossary endpoints, as opposed to the static ones compiled into Glossary.scala.
 *
 * Title is the resource key and is unique case insensitively, matching the way the static
 * Glossary is looked up.
 */
trait DynamicGlossaryItemProvider {

  def createDynamicGlossaryItem(
    title: String,
    description: String,
    overridesStaticItem: Boolean,
    createdByUserId: String
  ): Box[DynamicGlossaryItemTrait]

  def getDynamicGlossaryItemByTitle(title: String): Box[DynamicGlossaryItemTrait]

  def getDynamicGlossaryItems(
    titleFilter: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[DynamicGlossaryItemTrait], Int)]]

  /** Every Dynamic Glossary Item. Used to build the union returned by GET /api/glossary. */
  def getAllDynamicGlossaryItems: Box[List[DynamicGlossaryItemTrait]]

  /**
   * A cheap watermark that changes whenever any Dynamic Glossary Item is added, changed or
   * removed. GET /api/glossary caches its rendered JSON against this rather than for the life
   * of the JVM, so an edit on any node is picked up on the next call.
   */
  def getDynamicGlossaryItemsVersion: Box[String]

  def updateDynamicGlossaryItem(
    title: String,
    description: String,
    overridesStaticItem: Option[Boolean]
  ): Box[DynamicGlossaryItemTrait]

  def deleteDynamicGlossaryItem(title: String): Box[Boolean]
}

trait DynamicGlossaryItemTrait {
  def glossaryItemId: String
  def title: String
  def description: String
  /**
   * Declared intent: the operator said this item deliberately overrides a static Glossary Item of
   * the same title. Creating one that collides with a static title is refused unless this is set,
   * so shadowing is never an accident of title choice.
   */
  def overridesStaticItem: Boolean
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}
