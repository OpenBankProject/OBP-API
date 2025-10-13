/**
 * Open Bank Project - API
 * Copyright (C) 2011-2019, TESOBE GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Email: contact@tesobe.com
 * TESOBE GmbH.
 * Osloer Strasse 16/17
 * Berlin 13359, Germany
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 *
 */

package code.api.berlin.group.v1_3

import code.api.util.CustomJsonFormats
import code.model.ModeratedTransaction
import code.setup.PropsReset
import com.openbankproject.commons.model._
import net.liftweb.json._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}


class JSONFactory_BERLIN_GROUP_1_3Test extends FeatureSpec with Matchers with GivenWhenThen with PropsReset {

    implicit val formats = CustomJsonFormats.formats
  
    feature("test createTransactionJSON method") {
    scenario("createTransactionJSON should return a valid JSON object") {
      def mockModeratedTransaction(): ModeratedTransaction = {
        val mockThisBankAccount = new code.model.ModeratedBankAccount(
          accountId = AccountId("test-account-id"),
          owners = Some(Set.empty),
          accountType = Some("CURRENT"),
          balance = "1000.00",
          currency = Some("EUR"),
          label = None,
          nationalIdentifier = Some("NATID"),
          iban = Some("DE89370400440532013000"),
          number = Some("12345678"),
          bankName = Some("Test Bank"),
          bankId = BankId("test-bank-id"),
          bankRoutingScheme = Some("IBAN"),
          bankRoutingAddress = Some(""),
          accountRoutingScheme = Some("IBAN"),
          accountRoutingAddress = Some(""),
          accountRoutings = Nil,
          accountRules = Nil
        )

        val mockOtherBankAccount = new code.model.ModeratedOtherBankAccount(
          id = "other-id",
          label = AccountName("", NoAlias),
          nationalIdentifier = Some("NATID"),
          swift_bic = Some("BIC"),
          iban = Some(""),
          bankName = Some("Other Bank"),
          number = Some("87654321"),
          metadata = None,
          kind = Some("CURRENT"),
          bankRoutingScheme = Some("IBAN1"),
          bankRoutingAddress = Some("DE89370400440532013000"),
          accountRoutingScheme = Some("IBAN1"),
          accountRoutingAddress = Some("DE89370400440532013000")
        )

        new ModeratedTransaction(
          UUID = "uuid-1234",
          id = TransactionId("test-transaction-id"),
          bankAccount = Some(mockThisBankAccount),
          otherBankAccount = Some(mockOtherBankAccount),
          metadata = None,
          transactionType = Some("TRANSFER"),
          amount = Some(BigDecimal("100.00")),
          currency = Some("EUR"),
          description = Some("Test transaction"),
          startDate = Some(new java.util.Date()),
          finishDate = Some(new java.util.Date()),
          balance = "900.00",
          status = Some("booked")
        )
      }
      
      val transaction = mockModeratedTransaction() 

      val result = JSONFactory_BERLIN_GROUP_1_3.createTransactionJSON(transaction)

      result.transactionId shouldBe transaction.id.value
      
      result.transactionAmount.currency shouldBe transaction.currency.get
      result.bookingDate should not be empty
      result.valueDate should not be empty


      val jsonString: String = compactRender(Extraction.decompose(result))

      jsonString.contains("creditorName") shouldBe false 
      jsonString.contains("creditorAccount") shouldBe false 
      jsonString.contains("debtorName") shouldBe true
      jsonString.contains("debtorAccount") shouldBe true
      
      println(jsonString)
    }
  }
}
