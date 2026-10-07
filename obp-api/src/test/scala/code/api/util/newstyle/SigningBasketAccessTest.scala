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

package code.api.util.newstyle

import code.api.util.newstyle.SigningBasketNewStyle.{AuthorisationOperation, CreatorOnly, accessRefusal}
import code.setup.ServerSetup

/** Who may address a signing basket, as a pure rule: no request, no database. */
class SigningBasketAccessTest extends ServerSetup {

  private val tpp = Some("tpp-1")
  private val otherTpp = Some("tpp-2")
  private val psu = Some("psu-1")
  private val otherPsu = Some("psu-2")
  private val frontEnd = Some("portal")

  feature("the creating TPP addresses its own basket") {
    scenario("on every operation, with or without a PSU session") {
      List(CreatorOnly, AuthorisationOperation).foreach { access =>
        accessRefusal(tpp, None, tpp, None, callerIsScaFrontEnd = false, access) should equal(None)
        accessRefusal(tpp, psu, tpp, None, callerIsScaFrontEnd = false, access) should equal(None)
        accessRefusal(tpp, psu, tpp, psu, callerIsScaFrontEnd = false, access) should equal(None)
      }
    }

    scenario("but not when the PSU in its session is not the PSU the basket is for") {
      List(CreatorOnly, AuthorisationOperation).foreach { access =>
        accessRefusal(tpp, psu, tpp, otherPsu, callerIsScaFrontEnd = false, access) should not equal None
      }
    }
  }

  feature("another TPP addresses nothing, whoever the PSU is") {
    scenario("on every operation") {
      List(CreatorOnly, AuthorisationOperation).foreach { access =>
        accessRefusal(tpp, None, otherTpp, None, callerIsScaFrontEnd = false, access) should not equal None
        accessRefusal(tpp, psu, otherTpp, psu, callerIsScaFrontEnd = false, access) should not equal None
      }
    }
  }

  feature("the SCA front end acts on the authorisation and on nothing else") {
    scenario("it may drive the authorisation of a basket with no PSU yet, or for the PSU it names") {
      accessRefusal(tpp, None, frontEnd, None, callerIsScaFrontEnd = true, AuthorisationOperation) should equal(None)
      accessRefusal(tpp, None, frontEnd, psu, callerIsScaFrontEnd = true, AuthorisationOperation) should equal(None)
      accessRefusal(tpp, psu, frontEnd, psu, callerIsScaFrontEnd = true, AuthorisationOperation) should equal(None)
    }

    scenario("it may not drive the authorisation of a basket bound to another PSU") {
      accessRefusal(tpp, psu, frontEnd, otherPsu, callerIsScaFrontEnd = true, AuthorisationOperation) should not equal None
    }

    scenario("it may not read, check the status of, or delete the basket") {
      accessRefusal(tpp, None, frontEnd, None, callerIsScaFrontEnd = true, CreatorOnly) should not equal None
      accessRefusal(tpp, psu, frontEnd, psu, callerIsScaFrontEnd = true, CreatorOnly) should not equal None
    }

    scenario("a consumer that is not declared as the front end gets none of this") {
      accessRefusal(tpp, None, frontEnd, None, callerIsScaFrontEnd = false, AuthorisationOperation) should not equal None
    }
  }

  feature("a PSU's session under a second TPP is not that TPP's mandate") {
    scenario("the same PSU through another consumer is refused") {
      accessRefusal(tpp, psu, otherTpp, psu, callerIsScaFrontEnd = false, CreatorOnly) should not equal None
      accessRefusal(tpp, psu, otherTpp, psu, callerIsScaFrontEnd = false, AuthorisationOperation) should not equal None
    }
  }

  feature("a basket created before ownership was recorded belongs to nobody") {
    scenario("it is refused to every caller, the SCA front end included") {
      List(None, Some(""), Some("  ")).foreach { noOwner =>
        List(CreatorOnly, AuthorisationOperation).foreach { access =>
          accessRefusal(noOwner, None, tpp, None, callerIsScaFrontEnd = false, access) should not equal None
          accessRefusal(noOwner, None, frontEnd, psu, callerIsScaFrontEnd = true, access) should not equal None
        }
      }
    }
  }
}
