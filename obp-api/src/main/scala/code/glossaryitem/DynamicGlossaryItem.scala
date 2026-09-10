package code.glossaryitem

import code.util.MappedUUID
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

object MappedDynamicGlossaryItemProvider extends DynamicGlossaryItemProvider {

  override def createDynamicGlossaryItem(
    title: String,
    description: String,
    overridesStaticItem: Boolean,
    createdByUserId: String
  ): Box[DynamicGlossaryItemTrait] = {
    tryo {
      DynamicGlossaryItem.create
        .Title(title)
        .TitleLowerCase(title.toLowerCase)
        .Description(description)
        .OverridesStaticItem(overridesStaticItem)
        .CreatedByUserId(createdByUserId)
        .saveMe()
    }
  }

  override def getDynamicGlossaryItemByTitle(title: String): Box[DynamicGlossaryItemTrait] =
    DynamicGlossaryItem.find(By(DynamicGlossaryItem.TitleLowerCase, title.toLowerCase))

  override def getDynamicGlossaryItems(
    titleFilter: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[DynamicGlossaryItemTrait], Int)]] = Future {
    tryo {
      val baseQuery: List[QueryParam[DynamicGlossaryItem]] =
        titleFilter.map(t => Like(DynamicGlossaryItem.TitleLowerCase, s"%${t.toLowerCase}%")).toList
      // Count BEFORE applying limit/offset so the caller can page.
      val total: Int = DynamicGlossaryItem.count(baseQuery: _*).toInt
      val rows: List[DynamicGlossaryItem] = DynamicGlossaryItem.findAll(
        (baseQuery
          :+ OrderBy(DynamicGlossaryItem.TitleLowerCase, Ascending)
          :+ StartAt[DynamicGlossaryItem](offset)
          :+ MaxRows[DynamicGlossaryItem](limit)): _*
      )
      (rows.asInstanceOf[List[DynamicGlossaryItemTrait]], total)
    }
  }

  override def getAllDynamicGlossaryItems: Box[List[DynamicGlossaryItemTrait]] =
    tryo { DynamicGlossaryItem.findAll().asInstanceOf[List[DynamicGlossaryItemTrait]] }

  override def getDynamicGlossaryItemsVersion: Box[String] = tryo {
    // Row count catches inserts and deletes; the newest LastUpdate catches edits. A delete plus an
    // insert leaves the count unchanged but moves LastUpdate forward, so the pair is enough.
    val count = DynamicGlossaryItem.count
    val newest = DynamicGlossaryItem
      .findAll(OrderBy(DynamicGlossaryItem.LastUpdate, Descending), MaxRows[DynamicGlossaryItem](1))
      .headOption
      .map(_.LastUpdate.get.getTime)
      .getOrElse(0L)
    s"$count-$newest"
  }

  override def updateDynamicGlossaryItem(
    title: String,
    description: String,
    overridesStaticItem: Option[Boolean]
  ): Box[DynamicGlossaryItemTrait] = {
    DynamicGlossaryItem.find(By(DynamicGlossaryItem.TitleLowerCase, title.toLowerCase)).flatMap { row =>
      tryo {
        row.Description(description)
        overridesStaticItem.foreach(v => row.OverridesStaticItem(v))
        row.LastUpdate(new java.util.Date())
        row.saveMe()
      }
    }
  }

  override def deleteDynamicGlossaryItem(title: String): Box[Boolean] = {
    DynamicGlossaryItem.find(By(DynamicGlossaryItem.TitleLowerCase, title.toLowerCase)).flatMap { row =>
      tryo { row.delete_! }
    }
  }
}

class DynamicGlossaryItem extends DynamicGlossaryItemTrait with LongKeyedMapper[DynamicGlossaryItem] with IdPK {
  def getSingleton = DynamicGlossaryItem

  object GlossaryItemId extends MappedUUID(this)
  object Title extends MappedString(this, 255)
  // Lower-cased copy of Title, so uniqueness and lookup are case insensitive on every database
  // regardless of its collation. The static Glossary is looked up case insensitively too.
  object TitleLowerCase extends MappedString(this, 255)
  object Description extends MappedText(this) // Markdown, the same flavour the static Glossary uses
  // Declared intent to shadow a static Glossary Item of the same title. See the trait.
  object OverridesStaticItem extends MappedBoolean(this) {
    override def defaultValue = false
  }
  object CreatedByUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def glossaryItemId: String = GlossaryItemId.get
  override def title: String = Title.get
  override def description: String = Description.get
  override def overridesStaticItem: Boolean = OverridesStaticItem.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get
}

object DynamicGlossaryItem extends DynamicGlossaryItem with LongKeyedMetaMapper[DynamicGlossaryItem] {
  override def dbTableName = "DynamicGlossaryItem"
  // LastUpdate is indexed because getDynamicGlossaryItemsVersion reads the newest row on every
  // Glossary call, to decide whether the cached rendering is still valid.
  override def dbIndexes = UniqueIndex(TitleLowerCase) :: Index(LastUpdate) :: super.dbIndexes
}
