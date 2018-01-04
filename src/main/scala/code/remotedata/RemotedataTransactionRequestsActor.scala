package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.transactionrequests.TransactionRequests.{TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import code.transactionrequests.{MappedTransactionRequestProvider, RemotedataTransactionRequestsCaseClasses}
import code.util.Helper.MdcLoggable


class RemotedataTransactionRequestsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTransactionRequestProvider
  val cc = RemotedataTransactionRequestsCaseClasses

  def receive = {

    case cc.getMappedTransactionRequest(transactionRequestId: TransactionRequestId) =>
      logger.debug("getMappedTransactionRequest(" + transactionRequestId + ")")
      sender ! extractResult(mapper.getMappedTransactionRequest(transactionRequestId))

    case cc.getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId) =>
      logger.debug("getTransactionRequestsFromProvider(" + bankId + ", " + accountId + ", " + ")")
      sender ! extractResult(mapper.getTransactionRequestsFromProvider(bankId, accountId))

    case cc.getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId) =>
      logger.debug("getTransactionRequestFromProvider(" + transactionRequestId + ")")
      sender ! extractResult(mapper.getTransactionRequestFromProvider(transactionRequestId))

    case cc.updateAllPendingTransactionRequests() =>
      logger.debug("updateAllPendingTransactionRequests()")
      sender ! extractResult(mapper.updateAllPendingTransactionRequests)

    case cc.createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                          transactionRequestType: TransactionRequestType,
                                          account: BankAccount,
                                          counterparty: BankAccount,
                                          body: TransactionRequestBody,
                                          status: String,
                                          charge: TransactionRequestCharge) =>
      logger.debug("createTransactionRequestImpl(" + transactionRequestId + ", " +
                                                      transactionRequestType + ", " +
                                                      account + ", " +
                                                      counterparty + ", " +
                                                      body + ", " +
                                                      status + ", " +
                                                      charge + ", " +
                                                      ")")
      sender ! extractResult(mapper.createTransactionRequestImpl(transactionRequestId,
        transactionRequestType,
        account,
        counterparty,
        body,
        status,
        charge))

    case cc.createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                            transactionRequestType: TransactionRequestType,
                                            fromAccount: BankAccount,
                                            toAccount: BankAccount,
                                            transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                            details: String,
                                            status: String,
                                            charge: TransactionRequestCharge,
                                            chargePolicy: String) =>
      logger.debug("createTransactionRequestImpl210(" + transactionRequestId + ", " +
                                                        transactionRequestType + ", " +
                                                        fromAccount + ", " +
                                                        toAccount + ", " +
                                                        transactionRequestCommonBody + ", " +
                                                        details + ", " +
                                                        status + ", " +
                                                        charge + ", " +
                                                        chargePolicy + ", " +
                                                        ")")
      sender ! extractResult(mapper.createTransactionRequestImpl210(transactionRequestId,
                                                                    transactionRequestType,
                                                                    fromAccount,
                                                                    toAccount,
                                                                    transactionRequestCommonBody,
                                                                    details,
                                                                    status,
                                                                    charge,
                                                                    chargePolicy))

    case cc.saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId) =>
      logger.debug("saveTransactionRequestTransactionImpl(" + transactionRequestId + ", " +  transactionId + ")")
      sender ! extractResult(mapper.saveTransactionRequestTransactionImpl(transactionRequestId, transactionId))

    case cc.saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) =>
      logger.debug("saveTransactionRequestChallengeImpl(" + transactionRequestId + ", " +  challenge + ")")
      sender ! extractResult(mapper.saveTransactionRequestChallengeImpl(transactionRequestId, challenge))

    case cc.saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String) =>
      logger.debug("saveTransactionRequestStatusImpl(" + transactionRequestId + ", " +  status + ")")
      sender ! extractResult(mapper.saveTransactionRequestStatusImpl(transactionRequestId, status))

    case cc.bulkDeleteTransactionRequests() =>
      logger.debug("bulkDeleteTransactionRequests()")
      sender ! extractResult(mapper.bulkDeleteTransactionRequests())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

