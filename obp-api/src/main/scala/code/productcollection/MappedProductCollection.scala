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
    DoobieUtil.runQuery((selectColumns ++ condition).query[(String, String)].to[List])
      .map { case (collectionCode, productCode) =>
        MappedProductCollection(collectionCode, productCode) }

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
