package code.products

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{BankId, ProductCode}
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

/**
 * Doobie implementation of the product-tag store, replacing the Lift ProductTag entity.
 *
 * Written rather than ported: the reference branch never migrated this table, so there was no
 * prior implementation to audit. The behaviour it has to keep is pinned by
 * ProductTagsProviderTest, which was written against the Lift version first.
 *
 * Table "producttag" carries UniqueIndex(BankId, ProductCode, Tag), so setTags diffs the desired
 * set against what is stored and touches only the difference. That is deliberate and not just an
 * optimisation: truncate-and-reinsert would make two concurrent updates of disjoint tags collide
 * on the unique index, where a diff leaves untouched rows alone.
 *
 * Writes go through runUpdate, not runQuery: outside an http4s request scope runQuery's fallback
 * transactor is Strategy.void on an autoCommit=false pool, so the write would be rolled back when
 * the connection is returned.
 */
object DoobieProductTags {

  private def normalise(tags: List[String]): List[String] =
    tags.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct

  def getTags(bankId: BankId, productCode: ProductCode): List[String] =
    DoobieUtil.runQuery(
      sql"""SELECT tag FROM producttag
            WHERE bankid = ${bankId.value} AND productcode = ${productCode.value}"""
        .query[String].to[List]
    ).sorted

  def setTags(bankId: BankId, productCode: ProductCode, tags: List[String]): Box[List[String]] = tryo {
    val desired = normalise(tags).toSet
    val existing = getTags(bankId, productCode).toSet

    val toDelete = existing -- desired
    val toAdd = desired -- existing

    toDelete.foreach { tag =>
      DoobieUtil.runUpdate(
        sql"""DELETE FROM producttag
              WHERE bankid = ${bankId.value} AND productcode = ${productCode.value} AND tag = $tag"""
          .update.run)
    }
    toAdd.foreach { tag =>
      DoobieUtil.runUpdate(
        sql"""INSERT INTO producttag (bankid, productcode, tag)
              VALUES (${bankId.value}, ${productCode.value}, $tag)"""
          .update.run)
    }
    desired.toList.sorted
  }

  /** AND semantics: product codes carrying EVERY requested tag. Empty request matches nothing. */
  def getProductCodesWithAllTags(bankId: BankId, tags: List[String]): Set[String] = {
    val normalised = normalise(tags)
    if (normalised.isEmpty) return Set.empty
    val perTag: List[Set[String]] = normalised.map { t =>
      DoobieUtil.runQuery(
        sql"""SELECT productcode FROM producttag
              WHERE bankid = ${bankId.value} AND tag = $t"""
          .query[String].to[List]
      ).toSet
    }
    perTag.reduce(_ intersect _)
  }

  /** Batch lookup for list endpoints - one query returns all (code -> tags) for the bank. */
  def getTagsByProductCodes(bankId: BankId, productCodes: List[String]): Map[String, List[String]] = {
    if (productCodes.isEmpty) return Map.empty
    val inList = productCodes.map(c => fr"$c").reduceLeft((a, b) => a ++ fr"," ++ b)
    val rows = DoobieUtil.runQuery(
      (fr"SELECT productcode, tag FROM producttag WHERE bankid = ${bankId.value} AND productcode IN (" ++
        inList ++ fr")").query[(String, String)].to[List]
    )
    rows.groupBy(_._1).map { case (code, ts) => code -> ts.map(_._2).sorted }
  }
}
