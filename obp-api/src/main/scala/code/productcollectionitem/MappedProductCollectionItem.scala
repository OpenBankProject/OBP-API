package code.productcollectionitem

import code.api.util.DoobieUtil
import code.productattribute.DoobieProductAttributeProvider
import code.products.MappedProduct
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{ProductAttribute, ProductCollectionItem}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One member product of a collection. */
case class MappedProductCollectionItem(
  collectionCode: String,
  memberProductCode: String
) extends ProductCollectionItem

object MappedProductCollectionItem {

  private val selectColumns =
    fr"SELECT mcollectioncode, mmemberproductcode FROM mappedproductcollectionitem"

  private def query(condition: Fragment): List[MappedProductCollectionItem] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[(String, String)].to[List])
      .map { case (collectionCode, memberProductCode) =>
        MappedProductCollectionItem(collectionCode, memberProductCode) }

  def findAllByCollectionCode(collectionCode: String): List[MappedProductCollectionItem] =
    query(fr"WHERE mcollectioncode = $collectionCode")

  def deleteByCollectionCode(collectionCode: String): Int =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedproductcollectionitem WHERE mcollectioncode = $collectionCode".update.run)

  def insert(collectionCode: String, memberProductCode: String): MappedProductCollectionItem = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedproductcollectionitem
            (mcollectioncode, mmemberproductcode, createdat, updatedat)
            VALUES ($collectionCode, $memberProductCode, $now, $now)"""
        .update.run)
    MappedProductCollectionItem(collectionCode, memberProductCode)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductcollectionitem".update.run)
    ()
  }
}

object MappedProductCollectionItemProvider extends ProductCollectionItemProvider {

  override def getProductCollectionItems(collectionCode: String): Future[Box[List[MappedProductCollectionItem]]] = Future {
    tryo(MappedProductCollectionItem.findAllByCollectionCode(collectionCode))
  }

  override def getProductCollectionItemsTree(collectionCode: String, bankId: String) = Future {
    tryo {
      MappedProductCollectionItem.findAllByCollectionCode(collectionCode) map {
        productCollectionItem =>
          val product = MappedProduct.find(bankId, productCollectionItem.memberProductCode)
            .openOrThrowException("There is no product")
          val attributes: List[ProductAttribute] =
            DoobieProductAttributeProvider.getProductAttributesSync(bankId, product.code.value)
          val xxx: (ProductCollectionItem, MappedProduct, List[ProductAttribute]) = (productCollectionItem, product, attributes)
          xxx
      }
    }
  }

  /**
   * Replaces the collection's members wholesale: the existing rows go, then the supplied codes are
   * inserted. The unique index on (mcollectioncode, mmemberproductcode) means a caller passing the
   * same code twice in one list fails the whole call rather than silently storing a duplicate.
   */
  override def getOrCreateProductCollectionItem(collectionCode: String,
                                                memberProductCodes: List[String]): Future[Box[List[ProductCollectionItem]]] = Future {
    tryo {
      MappedProductCollectionItem.deleteByCollectionCode(collectionCode)
      memberProductCodes.map(MappedProductCollectionItem.insert(collectionCode, _))
    }
  }
}
