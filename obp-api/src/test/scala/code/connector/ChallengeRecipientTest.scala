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
package code.connector

import code.api.util.CommonsEmailWrapper
import code.bankconnectors.LocalMappedConnector
import code.customer.CustomerX
import code.setup.{PropsReset, ServerSetupWithTestData}
import code.usercustomerlinks.UserCustomerLink
import code.model.UserX
import com.openbankproject.commons.model.{BankId, CustomerFaceImage, User}
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthentication}
import net.liftweb.common.{Failure, Full}
import net.liftweb.util.Helpers.randomString

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Where the passcode of an EMAIL challenge goes: to the customers linked to the user, else to the user's own
 * address, and a challenge that no one could receive is not created.
 */
class ChallengeRecipientTest extends ServerSetupWithTestData with PropsReset {

  private def createUser(email: String): User =
    UserX.createResourceUser(
      provider = "challenge-recipient-test",
      providerId = Some(randomString(10)),
      createdByConsentId = None,
      name = Some(randomString(10)),
      email = Some(email),
      userId = None,
      company = None
    ).openOrThrowException("expected the user to be created")

  private def linkCustomer(user: User, customerEmail: String): Unit = {
    val customer = CustomerX.customerProvider.vend.addCustomer(
      BankId(testBankId1.value), randomString(10), "Test Customer", "+49 123 456", customerEmail,
      CustomerFaceImage(new Date(), "http://example.com/face.png"),
      new Date(), "single", 0, Nil, "Bachelor", "employed", true, new Date(),
      None, None, "", "", ""
    ).openOrThrowException("expected the customer to be created")
    UserCustomerLink.userCustomerLink.vend.getOCreateUserCustomerLink(user.userId, customer.customerId, new Date(), true)
  }

  private def createChallenge(user: User, method: StrongCustomerAuthentication.SCA) =
    Await.result(
      LocalMappedConnector.createChallengesC3(
        List(user.userId), ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE, Some(randomString(10)), Some(method),
        None, None, None, None, None
      ),
      10.seconds
    )._1

  private def challengeMails = CommonsEmailWrapper.testModeSentMail.filter(_.subject == "Challenge")

  private def startMailTestMode(): Unit = {
    setPropsValues("mail.test.mode" -> "true")
    CommonsEmailWrapper.clearTestModeSentMail()
  }

  feature("The passcode of an EMAIL challenge") {
    scenario("goes to the e-mail address of the linked customer") {
      startMailTestMode()
      val user = createUser("user-address@example.com")
      linkCustomer(user, "customer-address@example.com")

      createChallenge(user, StrongCustomerAuthentication.EMAIL).map(_.size) should equal(Full(1))

      challengeMails.map(_.to) should equal(List(List("customer-address@example.com")))
    }

    scenario("goes to the user's own address when no customer is linked") {
      startMailTestMode()
      val user = createUser("user-address@example.com")

      createChallenge(user, StrongCustomerAuthentication.EMAIL).map(_.size) should equal(Full(1))

      challengeMails.map(_.to) should equal(List(List("user-address@example.com")))
    }

    scenario("is not created when there is no address to send it to") {
      startMailTestMode()
      val user = createUser("")

      createChallenge(user, StrongCustomerAuthentication.EMAIL) shouldBe a[Failure]

      challengeMails shouldBe empty
    }
  }

  feature("The passcode of an SMS challenge") {
    scenario("is not created when no customer with a phone number is linked") {
      startMailTestMode()
      val user = createUser("user-address@example.com")

      createChallenge(user, StrongCustomerAuthentication.SMS) shouldBe a[Failure]
    }
  }
}
