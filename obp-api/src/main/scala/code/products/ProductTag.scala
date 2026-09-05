package code.products

import com.openbankproject.commons.model.{BankId, ProductCode}
import net.liftweb.common.Box

// Product tags keyed by (bank_id, product_code, tag). No FK to MappedProduct so tags work for
// connector-sourced products that have no local row.
//
// The Lift ProductTag entity is gone: the table is owned by Liquibase and the queries live in
// DoobieProductTags. This object stays as the call site's entry point so callers did not have to
// change, and delegates.
object ProductTagsProvider {
  def getTags(bankId: BankId, productCode: ProductCode): List[String] =
    DoobieProductTags.getTags(bankId, productCode)

  def setTags(bankId: BankId, productCode: ProductCode, tags: List[String]): Box[List[String]] =
    DoobieProductTags.setTags(bankId, productCode, tags)

  def getProductCodesWithAllTags(bankId: BankId, tags: List[String]): Set[String] =
    DoobieProductTags.getProductCodesWithAllTags(bankId, tags)

  def getTagsByProductCodes(bankId: BankId, productCodes: List[String]): Map[String, List[String]] =
    DoobieProductTags.getTagsByProductCodes(bankId, productCodes)
}
