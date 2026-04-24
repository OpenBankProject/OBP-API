package code.products

import code.util.UUIDString
import com.openbankproject.commons.model.{BankId, ProductCode}
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

// Product tags keyed by (bank_id, product_code, tag). No FK to MappedProduct so tags work for
// connector-sourced products that have no local row.
class ProductTag extends LongKeyedMapper[ProductTag] with IdPK {
  override def getSingleton = ProductTag

  object BankId extends UUIDString(this)
  object ProductCode extends MappedString(this, 50)
  object Tag extends MappedString(this, 100)
}

object ProductTag extends ProductTag with LongKeyedMetaMapper[ProductTag] {
  override def dbIndexes =
    UniqueIndex(BankId, ProductCode, Tag) ::
      Index(BankId, ProductCode) ::
      super.dbIndexes
}

// Normalisation and CRUD for product tags. Replace-semantics on setTags: diff old vs new rather
// than truncate + insert, so concurrent updates of disjoint tags stay race-free at the row level.
object ProductTagsProvider {

  private def normalise(tags: List[String]): List[String] =
    tags.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct

  def getTags(bankId: BankId, productCode: ProductCode): List[String] = {
    ProductTag.findAll(
      By(ProductTag.BankId, bankId.value),
      By(ProductTag.ProductCode, productCode.value)
    ).map(_.Tag.get).sorted
  }

  def setTags(bankId: BankId, productCode: ProductCode, tags: List[String]): Box[List[String]] = tryo {
    val desired = normalise(tags).toSet
    val existing = ProductTag.findAll(
      By(ProductTag.BankId, bankId.value),
      By(ProductTag.ProductCode, productCode.value)
    )
    val existingByTag: Map[String, ProductTag] = existing.map(t => t.Tag.get -> t).toMap

    val toDelete = existing.filterNot(t => desired.contains(t.Tag.get))
    val toAdd = desired.filterNot(existingByTag.contains)

    toDelete.foreach(_.delete_!)
    toAdd.foreach { tag =>
      ProductTag.create
        .BankId(bankId.value)
        .ProductCode(productCode.value)
        .Tag(tag)
        .saveMe()
    }
    desired.toList.sorted
  }

  // AND semantics: returns product codes that carry EVERY requested tag.
  def getProductCodesWithAllTags(bankId: BankId, tags: List[String]): Set[String] = {
    val normalised = normalise(tags)
    if (normalised.isEmpty) return Set.empty
    val perTag: List[Set[String]] = normalised.map { t =>
      ProductTag.findAll(
        By(ProductTag.BankId, bankId.value),
        By(ProductTag.Tag, t)
      ).map(_.ProductCode.get).toSet
    }
    perTag.reduce(_ intersect _)
  }

  // Batch lookup for list endpoints — one query returns all (code -> tags) for the bank.
  def getTagsByProductCodes(bankId: BankId, productCodes: List[String]): Map[String, List[String]] = {
    if (productCodes.isEmpty) return Map.empty
    val rows = ProductTag.findAll(
      By(ProductTag.BankId, bankId.value),
      ByList(ProductTag.ProductCode, productCodes)
    )
    rows.groupBy(_.ProductCode.get).map { case (code, ts) => code -> ts.map(_.Tag.get).sorted }
  }
}
