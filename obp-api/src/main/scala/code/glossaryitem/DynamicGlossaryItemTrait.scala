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
 * /glossary-items endpoints, as opposed to the static ones compiled into Glossary.scala.
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
