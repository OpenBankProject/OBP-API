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

package code.products

/* For products */

// Need to import these one by one because in same package!

import com.openbankproject.commons.model.{BankId, ProductCode}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.Product

object Products extends SimpleInjector {

  val productsProvider = new Inject(() => buildOne) {}

  def buildOne: ProductsProvider = MappedProductsProvider

  // Helper to get the count out of an option
  def countOfProducts (listOpt: Option[List[Product]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait ProductsProvider extends MdcLoggable {


  /*
  Common logic for returning products.
  Use adminView = true to get all Products, else only ones with license returned.
   */
  final def getProducts(bankId : BankId, adminView: Boolean = false) : Option[List[Product]] = {
    logger.info(s"Hello from getProducts bankId is: $bankId")
    getProductsFromProvider(bankId)
  }

  /*
  Return one Product at a bank
   */
  final def getProduct(bankId : BankId, productCode : ProductCode, adminView: Boolean = false) : Option[Product] = {
      getProductFromProvider(bankId, productCode)
  }

  protected def getProductFromProvider(bankId : BankId, productCode : ProductCode) : Option[Product]
  protected def getProductsFromProvider(bank : BankId) : Option[List[Product]]

// End of Trait
}
