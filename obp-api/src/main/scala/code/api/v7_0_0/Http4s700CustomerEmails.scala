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

import cats.effect.IO
import code.api.Constant
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{Http4sRoute, ResourceDoc, UserXORApplication, unboxFullOrFail}
import code.api.util.ApiRole.canSendEmailToCustomerAtOneBank
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, CommonsEmailWrapper, CustomJsonFormats}
import code.customer.CustomerX
import code.messageoutbox.MessageOutbox
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.{By, By_>}
import net.liftweb.util.Helpers.tryo
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats
import org.json4s.native.Serialization

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds Send Email to Customer: a bank, or an application acting for it such as a programme website,
 * tells one of its Customers about something (their application was received, a decision was made). A Customer is
 * the bank's legal person, so the email goes to someone the space has a relationship with. OBP-API reads the address
 * from the Customer and queues the email in the message outbox, whose relay sends it; the caller never sees it.
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700CustomerEmails {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val OperationCustomerEmail = "customer_email"
  val SubjectTypeCustomerId = "customer_id"
  val MaxSubjectLength = 200
  val MaxTextLength = 20000
  val MaxReferenceLength = 255

  object CustomerEmails {
    val DefaultMaxPerCustomerPerDay: Int = 20

    def enabledIn(bankId: String): Boolean =
      APIUtil.getPropsValue("customer_emails.enabled_bank_ids").toOption.toList
        .flatMap(_.split(",").map(_.trim).filter(_.nonEmpty)).contains(bankId)
    def maxPerCustomerPerDay: Int =
      APIUtil.getPropsAsIntValue("customer_emails.max_per_customer_per_day").openOr(DefaultMaxPerCustomerPerDay)
    /** The From address: its own prop, else the one the "role granted" email uses. */
    def senderAddress: Option[String] =
      APIUtil.getPropsValue("customer_emails.sender_address")
        .or(APIUtil.getPropsValue("mail.api.consumer.registered.sender.address"))
        .toOption.map(_.trim).filter(_.nonEmpty)
  }

  /** An address the outbox can send to: one line, one @, no spaces. Not proof that it works. */
  private def plausibleAddress(email: String): Boolean =
    email.length <= 254 && email.count(_ == '@') == 1 && !email.exists(c => c.isWhitespace || c == ',' || c == ';') &&
      email.indexOf('@') > 0 && email.indexOf('@') < email.length - 1

  /** Emails queued for a Customer in the last 24 hours, by any application. */
  private def sentInLastDay(customerId: String): Long =
    MessageOutbox.count(
      By(MessageOutbox.OutboxType, MessageOutbox.TYPE_EMAIL),
      By(MessageOutbox.OperationName, OperationCustomerEmail),
      By(MessageOutbox.SubjectId, customerId),
      By_>(MessageOutbox.CreatedAt, new java.util.Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000))
    )

  // Route: POST /obp/v7.0.0/banks/BANK_ID/customers/CUSTOMER_ID/emails
  lazy val sendEmailToCustomer: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / bankIdInUrl / "customers" / customerId / "emails" if customerId.nonEmpty =>
      EndpointHelpers.withUserAndBodyCreated[PostCustomerEmailJsonV700, CustomerEmailJsonV700](req) { (caller, body, cc) =>
        val subject = Option(body.subject).map(_.trim).getOrElse("")
        val text = Option(body.text).map(_.trim).getOrElse("")
        val reference = body.reference.map(_.trim).filter(_.nonEmpty)
        val sender = cc.consumer.map(_.name.get).toOption.filter(_.trim.nonEmpty).getOrElse("an application")
        for {
          _ <- Helper.booleanToFuture(s"$CustomerEmailsNotEnabled Current bank_id is $bankIdInUrl.", 403, Some(cc)) {
            CustomerEmails.enabledIn(bankIdInUrl)
          }
          from <- Future(CustomerEmails.senderAddress)
            .map(unboxFullOrFail(_, Some(cc), CustomerEmailSenderNotConfigured, 503))
          _ <- Helper.booleanToFuture(s"$InvalidJsonFormat subject must be 1 to $MaxSubjectLength characters on one line.", 400, Some(cc)) {
            subject.nonEmpty && subject.length <= MaxSubjectLength && !subject.exists(c => c == '\r' || c == '\n')
          }
          _ <- Helper.booleanToFuture(s"$InvalidJsonFormat text must be 1 to $MaxTextLength characters.", 400, Some(cc)) {
            text.nonEmpty && text.length <= MaxTextLength
          }
          _ <- Helper.booleanToFuture(s"$InvalidJsonFormat reference must be at most $MaxReferenceLength characters.", 400, Some(cc)) {
            reference.forall(_.length <= MaxReferenceLength)
          }
          // A Customer of another bank is not found here: the space only emails its own Customers.
          customer <- Future(CustomerX.customerProvider.vend.getCustomerByCustomerId(customerId).filter(_.bankId == bankIdInUrl))
            .map(unboxFullOrFail(_, Some(cc), s"$CustomerNotFoundByCustomerId Current CUSTOMER_ID is $customerId", 404))
          address = Option(customer.email).map(_.trim).getOrElse("")
          _ <- Helper.booleanToFuture(CustomerHasNoEmail, 400, Some(cc))(plausibleAddress(address))
          _ <- Helper.booleanToFuture(s"$CustomerEmailLimitReached At most ${CustomerEmails.maxPerCustomerPerDay} emails a day.", 429, Some(cc)) {
            sentInLastDay(customer.customerId) < CustomerEmails.maxPerCustomerPerDay
          }
          footer = s"\n\n--\nSent by $sender through ${Constant.HostName}."
          metadata = Serialization.write(Map(
            "bank_id" -> Some(bankIdInUrl),
            "consumer_id" -> cc.consumer.map(_.consumerId.get).toOption,
            "sent_by_user_id" -> Some(caller.userId),
            "reference" -> reference
          ).collect { case (k, Some(v)) => k -> v })
          row <- Future(tryo(MessageOutbox.enqueueEmail(
            subjectId = customer.customerId,
            subjectIdType = SubjectTypeCustomerId,
            operationName = OperationCustomerEmail,
            CommonsEmailWrapper.EmailContent(from = from, to = List(address), subject = subject,
              textContent = Some(text + footer)),
            metadataJson = metadata
          ))).map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not queue the email.", 400))
        } yield JSONFactory700CustomerEmails.createCustomerEmailJson(row, bankIdInUrl, customer.customerId, subject, reference)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(sendEmailToCustomer),
    "POST",
    "/banks/BANK_ID/customers/CUSTOMER_ID/emails",
    "Send Email to Customer",
    s"""Queues a plain-text email to the Customer CUSTOMER_ID of BANK_ID, at the email address on the Customer record,
       |for example to confirm that their application was received or to tell them a decision was made. The address is
       |never returned. The message outbox sends the email and retries it until it is delivered.
       |
       |The body has a `subject` (1 to $MaxSubjectLength characters, one line), the `text` (1 to $MaxTextLength characters)
       |and an optional `reference` (at most $MaxReferenceLength characters), for example the record the email is about,
       |kept with the queued message. A line saying which application sent the email is added to the text.
       |
       |**Limits.** Off unless BANK_ID is in the prop `customer_emails.enabled_bank_ids`. Only a Customer of BANK_ID is
       |emailed. At most `customer_emails.max_per_customer_per_day` (default ${CustomerEmails.DefaultMaxPerCustomerPerDay})
       |emails are queued for a Customer in 24 hours, by all applications together. The From address is
       |`customer_emails.sender_address`, else `mail.api.consumer.registered.sender.address`.
       |
       |**Who may call it.** A User with the Role CanSendEmailToCustomerAtOneBank at BANK_ID, or an application acting as
       |itself whose Consumer holds it as a Scope.
       |
       |Authentication is Required.""".stripMargin,
    JSONFactory700CustomerEmails.postCustomerEmailJsonExample,
    JSONFactory700CustomerEmails.customerEmailJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, CustomerEmailsNotEnabled,
      CustomerEmailSenderNotConfigured, CustomerNotFoundByCustomerId, CustomerHasNoEmail, CustomerEmailLimitReached,
      UnknownError),
    List(apiTagCustomer),
    Some(List(canSendEmailToCustomerAtOneBank)),
    authMode = UserXORApplication,
    http4sPartialFunction = Some(sendEmailToCustomer)
  ).allowSystemSpace()
}
