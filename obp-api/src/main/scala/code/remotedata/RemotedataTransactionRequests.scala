package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit

import com.openbankproject.commons.model.{TransactionRequest, TransactionRequestChallenge, TransactionRequestCharge, _}
import code.transactionrequests.{MappedTransactionRequest, RemotedataTransactionRequestsCaseClasses, TransactionRequestProvider}

import net.liftweb.common.Box

object RemotedataTransactionRequests extends ObpActorInit with TransactionRequestProvider {

  val cc = RemotedataTransactionRequestsCaseClasses

  def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest] = getValueFromFuture(
    (actor ? cc.getMappedTransactionRequest(transactionRequestId)).mapTo[Box[MappedTransactionRequest]]
  )

  def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]] = getValueFromFuture(
    (actor ? cc.getTransactionRequestsFromProvider(bankId, accountId)).mapTo[Box[List[TransactionRequest]]]
  )

  def getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId): Box[TransactionRequest] = getValueFromFuture(
    (actor ? cc.getTransactionRequestFromProvider(transactionRequestId)).mapTo[Box[TransactionRequest]]
  )

  def updateAllPendingTransactionRequests(): Box[Option[Unit]] = getValueFromFuture(
    (actor ? cc.updateAllPendingTransactionRequests()).mapTo[ Box[Option[Unit]]]
  )

  def createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                   transactionRequestType: TransactionRequestType,
                                   account: BankAccount,
                                   counterparty: BankAccount,
                                   body: TransactionRequestBody,
                                   status: String,
                                   charge: TransactionRequestCharge): Box[TransactionRequest] = getValueFromFuture(
    (actor ? cc.createTransactionRequestImpl(
      transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge)).mapTo[Box[TransactionRequest]]
  )

  def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                      transactionRequestType: TransactionRequestType,
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                      details: String,
                                      status: String,
                                      charge: TransactionRequestCharge,
                                      chargePolicy: String): Box[TransactionRequest] = getValueFromFuture(
    (actor ? cc.createTransactionRequestImpl210(
      transactionRequestId,
      transactionRequestType,
      fromAccount,
      toAccount,
      transactionRequestCommonBody,
      details,
      status,
      charge,
      chargePolicy)).mapTo[Box[TransactionRequest]]
  )

  def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = getValueFromFuture(
    (actor ? cc.saveTransactionRequestTransactionImpl(transactionRequestId, transactionId)).mapTo[Box[Boolean]]
  )

  def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = getValueFromFuture(
    (actor ? cc.saveTransactionRequestChallengeImpl(transactionRequestId, challenge)).mapTo[Box[Boolean]]
  )

  def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = getValueFromFuture(
    (actor ? cc.saveTransactionRequestStatusImpl(transactionRequestId, status)).mapTo[Box[Boolean]]
  )

  def bulkDeleteTransactionRequests(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteTransactionRequests()).mapTo[Boolean]
  )


}
