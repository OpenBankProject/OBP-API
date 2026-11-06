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

package code.transactionrequests


import code.api.util.{APIUtil, CallContext}
import com.openbankproject.commons.model.{TransactionRequest, TransactionRequestChallenge, TransactionRequestCharge, _}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object TransactionRequests extends SimpleInjector {

  def updatestatus(newStatus: String) = {}

  val transactionRequestProvider = new Inject(() => buildOne) {}

  def buildOne: TransactionRequestProvider  =
    APIUtil.getPropsValue("transactionRequests_connector", "mapped") match {
      case "mapped" =>MappedTransactionRequestProvider
      case tc: String => throw new IllegalArgumentException("No such connector for Transaction Requests: " + tc)
    }

  // Helper to get the count out of an option
  def countOfTransactionRequests(listOpt: Option[List[TransactionRequest]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }

}

trait TransactionRequestProvider extends MdcLoggable {

  final def getTransactionRequest(transactionRequestId : TransactionRequestId) : Box[TransactionRequest] = {
    getTransactionRequestFromProvider(transactionRequestId)
  }

  final def getTransactionRequests(bankId : BankId, accountId: AccountId) : Box[List[TransactionRequest]] = {
    getTransactionRequestsFromProvider(bankId, accountId)
  }

  def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest]
  def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]]
  def getTransactionRequestFromProvider(transactionRequestId : TransactionRequestId) : Box[TransactionRequest]
  def updateAllPendingTransactionRequests: Box[Option[Unit]]
  /**
   *
   * @param transactionRequestId
   * @param transactionRequestType Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
   * @param fromAccount
   * @param toAccount
   * @param transactionRequestCommonBody Body from http request: should have common fields:
   * @param details  This is the details / body of the request (contains all fields in the body)
   * @param status   "INITIATED" "PENDING" "FAILED"  "COMPLETED"
   * @param charge
   * @param chargePolicy  SHARED, SENDER, RECEIVER
   * @return  Always create a new Transaction Request in mapper, and return all the fields
   */
  def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                      transactionRequestType: TransactionRequestType,
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                      details: String,
                                      status: String,
                                      charge: TransactionRequestCharge,
                                      chargePolicy: String,
                                      paymentService: Option[String],
                                      berlinGroupPayments: Option[BerlinGroupTransactionRequestCommonBodyJson],
                                      apiStandard: Option[String],
                                      apiVersion: Option[String],
                                      callContext: Option[CallContext]): Box[TransactionRequest]

  def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean]
  def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean]
  def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean]
  def saveTransactionRequestDescriptionImpl(transactionRequestId: TransactionRequestId, description: String): Box[Boolean]
  def bulkDeleteTransactionRequestsByTransactionId(transactionId: TransactionId): Boolean
  def bulkDeleteTransactionRequests(): Boolean
}
