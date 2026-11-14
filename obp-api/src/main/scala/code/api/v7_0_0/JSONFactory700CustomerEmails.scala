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
package code.api.v7_0_0

import java.util.Date

import code.messageoutbox.MessageOutbox

/** The body of Send Email to Customer: plain text only. */
case class PostCustomerEmailJsonV700(
  subject: String,
  text: String,
  reference: Option[String]
)

/** A queued email, as Send Email to Customer returns it. The address is never returned. */
case class CustomerEmailJsonV700(
  message_id: String,
  bank_id: String,
  customer_id: String,
  subject: String,
  reference: Option[String],
  status: String,
  created_at: Date
)

object JSONFactory700CustomerEmails {
  val postCustomerEmailJsonExample: PostCustomerEmailJsonV700 = PostCustomerEmailJsonV700(
    subject = "Your application was received",
    text = "Dear applicant,\n\nThank you: your application to Cohort 1 was received on 20 November.",
    reference = Some("cohort_application:6fb17583-1e49-4435-bb74-a14fe0996723")
  )

  val customerEmailJsonExample: CustomerEmailJsonV700 = CustomerEmailJsonV700(
    message_id = "1042",
    bank_id = "cohort",
    customer_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de9f0",
    subject = "Your application was received",
    reference = Some("cohort_application:6fb17583-1e49-4435-bb74-a14fe0996723"),
    status = MessageOutbox.STATUS_PENDING,
    created_at = new Date(1795000000000L)
  )

  def createCustomerEmailJson(row: MessageOutbox, bankId: String, customerId: String, subject: String, reference: Option[String]): CustomerEmailJsonV700 =
    CustomerEmailJsonV700(row.id.toString, bankId, customerId, subject, reference, row.status, row.createdAt)
}
