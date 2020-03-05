package code.productcollection

import com.openbankproject.commons.model.ProductCollection
import net.liftweb.common._
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedProductCollectionProvider extends ProductCollectionProvider {
  override def getProductCollection(collectionCode: String): Future[Box[List[ProductCollection]]] = Future {
    tryo(MappedProductCollection.findAll(By(MappedProductCollection.mCollectionCode, collectionCode)))
  }

  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String]): Future[Box[List[ProductCollection]]] = Future {
    tryo {
      val deleted = 
        for {
          item <- MappedProductCollection.findAll(By(MappedProductCollection.mCollectionCode, collectionCode))
        } yield item.delete_!
  
      val result: List[MappedProductCollection] = deleted.forall(_ == true) match {
        case true =>
          for {
            productCode <- productCodes
          } yield {
            MappedProductCollection
              .create
              .mProductCode(productCode)
              .mCollectionCode(collectionCode)
              .saveMe
          }
        case false =>
          Nil
      }
      result
    }
  }
}

class MappedProductCollection extends ProductCollection with LongKeyedMapper[MappedProductCollection] with IdPK with CreatedUpdated {
  
  def getSingleton = MappedProductCollection

  object mCollectionCode extends MappedString(this, 50)
  object mProductCode extends MappedString(this, 50)

  override def collectionCode: String = mCollectionCode.get
  override def productCode: String = mProductCode.get
  
}


object MappedProductCollection extends MappedProductCollection with LongKeyedMetaMapper[MappedProductCollection] {
  override def dbIndexes: List[BaseIndex[MappedProductCollection]] = UniqueIndex(mCollectionCode, mProductCode) :: super.dbIndexes
}