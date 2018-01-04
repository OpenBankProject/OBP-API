package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import code.transactionrequests.{MappedTransactionRequest, RemotedataTransactionRequestsCaseClasses, TransactionRequestProvider}
import net.liftweb.common.Box

object RemotedataTransactionRequests extends ObpActorInit with TransactionRequestProvider {

  val cc = RemotedataTransactionRequestsCaseClasses

  def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest] =
    extractFutureToBox(actor ? cc.getMappedTransactionRequest(transactionRequestId))

  def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]] =
    extractFutureToBox(actor ? cc.getTransactionRequestsFromProvider(bankId, accountId))

  def getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId): Box[TransactionRequest] =
    extractFutureToBox(actor ? cc.getTransactionRequestFromProvider(transactionRequestId))

  def updateAllPendingTransactionRequests(): Box[Option[Unit]] =
    extractFutureToBox(actor ? cc.updateAllPendingTransactionRequests())

  def createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                   transactionRequestType: TransactionRequestType,
                                   account: BankAccount,
                                   counterparty: BankAccount,
                                   body: TransactionRequestBody,
                                   status: String,
                                   charge: TransactionRequestCharge): Box[TransactionRequest] =
    extractFutureToBox(actor ? cc.createTransactionRequestImpl(
      transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge))

  def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                      transactionRequestType: TransactionRequestType,
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                      details: String,
                                      status: String,
                                      charge: TransactionRequestCharge,
                                      chargePolicy: String): Box[TransactionRequest] =
    extractFutureToBox(actor ? cc.createTransactionRequestImpl210(
      transactionRequestId,
      transactionRequestType,
      fromAccount,
      toAccount,
      transactionRequestCommonBody,
      details,
      status,
      charge,
      chargePolicy))

  def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] =
    extractFutureToBox(actor ? cc.saveTransactionRequestTransactionImpl(transactionRequestId, transactionId))

  def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] =
    extractFutureToBox(actor ? cc.saveTransactionRequestChallengeImpl(transactionRequestId, challenge))

  def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] =
    extractFutureToBox(actor ? cc.saveTransactionRequestStatusImpl(transactionRequestId, status))

  def bulkDeleteTransactionRequests(): Boolean =
    extractFuture(actor ? cc.bulkDeleteTransactionRequests())


}
