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
    DoobieUtil.runQuery((selectColumns ++ condition).query[(Option[String], Option[String])].to[List])
      .map { case (collectionCode, memberProductCode) =>
        MappedProductCollectionItem(collectionCode.orNull, memberProductCode.orNull) }

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
