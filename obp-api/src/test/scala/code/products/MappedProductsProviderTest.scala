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

import com.openbankproject.commons.model.Product
import code.setup.ServerSetup
import com.openbankproject.commons.model.BankId
import net.liftweb.mapper.By

class MappedProductsProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedProduct.bulkDelete_!!()
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  def defaultSetup() = new DefaultSetup()
  
  class DefaultSetup {
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 products for bank X (one product does not have a license)

      val unlicensedProduct = MappedProduct.create
        .mBankId(bankIdX)
        .mCode("code-unlicensed")
        .mName("Name Unlicensed")
        .mCategory("Cat U")
        .mFamily("Family U")
        .mSuperFamily("Super Fam U")
        .mMoreInfoUrl("www.example.com/moreu")
        .mLicenseId("") // Note: The license is not set
        .mLicenseName("") // Note: The license is not set
        .saveMe()



      val product1 = MappedProduct.create
        .mBankId(bankIdX)
        .mCode("code-1")
        .mName("Product Name 1")
        .mCategory("Cat 1")
        .mFamily("Family 1")
        .mSuperFamily("Super Fam 1")
        .mMoreInfoUrl("www.example.com/more1")
        .mLicenseId("some-license")
        .mLicenseName("Some License")
        .saveMe()

      val product2 = MappedProduct.create
        .mBankId(bankIdX)
        .mCode("code-2")
        .mName("Product Name 2")
        .mCategory("Cat 2")
        .mFamily("Family 2")
        .mSuperFamily("Super Fam 2")
        .mMoreInfoUrl("www.example.com/more2")
        .mLicenseId("some-license")
        .mLicenseName("Some License")
        .saveMe()
    }


  feature("MappedProductsProvider") {

    scenario("We try to get Products") {

      val fixture = defaultSetup()

      // Only these have license set
      val expectedProducts =  List(fixture.product1, fixture.product2, fixture.unlicensedProduct)


      Given("the bank in question has Products")
      MappedProduct.find(By(MappedProduct.mBankId, fixture.bankIdX)).isDefined should equal(true)

      When("we try to get the Products for that bank")
      val productsOpt: Option[List[Product]] = MappedProductsProvider.getProducts(BankId(fixture.bankIdX))

      Then("We should get a Products list")
      productsOpt.isDefined should equal (true)
      val products = productsOpt.get

      And("it should contain 3 Products")
      products.size should equal(3)

      And("they should be the licensed ones")
      products.sortBy(_.code.value) should equal (expectedProducts.sortBy(_.code.value))
    }

    scenario("We try to get Products for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any Products")

      MappedProduct.find(By(MappedProduct.mBankId, fixture.bankIdY)).isDefined should equal(false)

      When("we try to get the Products for that bank")
      val productsOpt = MappedProductsProvider.getProducts(BankId(fixture.bankIdY))

      Then("we should get back an empty list")
      productsOpt.isDefined should equal(true)
      val products = productsOpt.get

      products.size should equal(0)

    }


    // TODO add test for individual items

  }
}
