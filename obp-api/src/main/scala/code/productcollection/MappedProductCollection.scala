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