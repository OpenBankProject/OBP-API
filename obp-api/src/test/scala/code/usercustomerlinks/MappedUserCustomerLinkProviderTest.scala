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

package code.usercustomerlinks

import java.util.Date

import code.setup.ServerSetup
import net.liftweb.common.Full

class MappedUserCustomerLinkProviderTest extends ServerSetup {

    val customerId1 = "5ada3287-c045-4683-a28f-492c19787460"
    val userId1 = "0ea67c89-d51b-4b24-9fd8-3ee535c2b473"
    val customerId2 = "551d2aaa-e5af-416e-ba82-25154d65a9cf"
    val userId2 = "3febd61e-3551-460d-a2a0-128a8a177d19"

    def userCustomerLink(userId: String, customerId: String) = UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(userId, customerId, new Date(12340000), true)

  private def delete(): Unit = {
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }


  feature("Getting user to customer link data") {

    scenario("We try to get UserCustomerLink") {
      Given("There is no user to customer link at all but we try to get it")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinks.getOrElse(List()).size should equal(0)

      When("We try to get it all")
      val found = UserCustomerLink.userCustomerLink.vend.getUserCustomerLinks.getOrElse(List())

      Then("We don't")
      found.size should equal(0)
    }


    scenario("A UserCustomerLink exists for user and we try to get it") {
      val userCustomerLink1 = userCustomerLink(userId1, customerId1)
      Given("Create a user to customer link")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(userId1, customerId1).isDefined should equal(true)

      When("We try to get it by user and customer")
      val foundOpt = UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(userId1, customerId1)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("It is the right thing")
      val foundThing = foundOpt
      foundThing should equal(userCustomerLink1)

      And("Primary id should be UUID")
      val customerId = foundThing.map(x => x.userCustomerLinkId).getOrElse("").filter(_ != '-')
      customerId.length should equal(32)
    }

    scenario("We try to get all UserCustomerLink rows"){
      val userCustomerLink1 = userCustomerLink(userId1, customerId1)
      val userCustomerLink2 = userCustomerLink(userId2, customerId2)

      When("We try to get it all")
      val found: List[UserCustomerLink] = UserCustomerLink.userCustomerLink.vend.getUserCustomerLinks.getOrElse(List())

      Then("We don't")
      found.size should equal(2)

      And("We try to get it by user1 and customer1")
      val foundThing1 = found.filter(_.userId == userId1).filter(_.customerId == customerId1).map(x=>Full(x))
      foundThing1 should equal(List(userCustomerLink1))

      And("We try to get it by user2 and customer2")
      val foundThing2 = found.filter(_.userId == userId2).filter(_.customerId == customerId2).map(x=>Full(x))
      foundThing2 should equal(List(userCustomerLink2))

    }

  }
}
