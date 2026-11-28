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

package code.productcollection

import code.api.util.DoobieUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.ProductCollection
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One product's membership of a named collection. */
case class MappedProductCollection(
  collectionCode: String,
  productCode: String
) extends ProductCollection

object MappedProductCollection {

  private val selectColumns =
    fr"SELECT mcollectioncode, mproductcode FROM mappedproductcollection"

  private def query(condition: Fragment): List[MappedProductCollection] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[(Option[String], Option[String])].to[List])
      .map { case (collectionCode, productCode) =>
        MappedProductCollection(collectionCode.orNull, productCode.orNull) }

  def findAllByCollectionCode(collectionCode: String): List[MappedProductCollection] =
    query(fr"WHERE mcollectioncode = $collectionCode")

  def deleteByCollectionCode(collectionCode: String): Int =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedproductcollection WHERE mcollectioncode = $collectionCode".update.run)

  def insert(collectionCode: String, productCode: String): MappedProductCollection = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedproductcollection
            (mcollectioncode, mproductcode, createdat, updatedat)
            VALUES ($collectionCode, $productCode, $now, $now)"""
        .update.run)
    MappedProductCollection(collectionCode, productCode)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductcollection".update.run)
    ()
  }
}

object MappedProductCollectionProvider extends ProductCollectionProvider {

  override def getProductCollection(collectionCode: String): Future[Box[List[ProductCollection]]] = Future {
    tryo(MappedProductCollection.findAllByCollectionCode(collectionCode))
  }

  /**
   * Replaces the collection wholesale: the existing rows go, then the supplied product codes are
   * inserted. The unique index on (mcollectioncode, mproductcode) means a caller passing the same
   * code twice in one list fails the whole call rather than silently storing a duplicate.
   */
  override def getOrCreateProductCollection(collectionCode: String,
                                            productCodes: List[String]): Future[Box[List[ProductCollection]]] = Future {
    tryo {
      MappedProductCollection.deleteByCollectionCode(collectionCode)
      productCodes.map(MappedProductCollection.insert(collectionCode, _))
    }
  }
}
