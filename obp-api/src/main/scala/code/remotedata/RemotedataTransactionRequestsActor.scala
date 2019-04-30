package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionrequests.{MappedTransactionRequestProvider, RemotedataTransactionRequestsCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._


class RemotedataTransactionRequestsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTransactionRequestProvider
  val cc = RemotedataTransactionRequestsCaseClasses

  def receive = {

    case cc.getMappedTransactionRequest(transactionRequestId: TransactionRequestId) =>
      logger.debug("getMappedTransactionRequest(" + transactionRequestId + ")")
      sender ! (mapper.getMappedTransactionRequest(transactionRequestId))

    case cc.getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId) =>
      logger.debug("getTransactionRequestsFromProvider(" + bankId + ", " + accountId + ", " + ")")
      sender ! (mapper.getTransactionRequestsFromProvider(bankId, accountId))

    case cc.getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId) =>
      logger.debug("getTransactionRequestFromProvider(" + transactionRequestId + ")")
      sender ! (mapper.getTransactionRequestFromProvider(transactionRequestId))

    case cc.updateAllPendingTransactionRequests() =>
      logger.debug("updateAllPendingTransactionRequests()")
      sender ! (mapper.updateAllPendingTransactionRequests)

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
      sender ! (mapper.createTransactionRequestImpl(transactionRequestId,
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
      sender ! (mapper.createTransactionRequestImpl210(transactionRequestId,
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
      sender ! (mapper.saveTransactionRequestTransactionImpl(transactionRequestId, transactionId))

    case cc.saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) =>
      logger.debug("saveTransactionRequestChallengeImpl(" + transactionRequestId + ", " +  challenge + ")")
      sender ! (mapper.saveTransactionRequestChallengeImpl(transactionRequestId, challenge))

    case cc.saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String) =>
      logger.debug("saveTransactionRequestStatusImpl(" + transactionRequestId + ", " +  status + ")")
      sender ! (mapper.saveTransactionRequestStatusImpl(transactionRequestId, status))

    case cc.bulkDeleteTransactionRequests() =>
      logger.debug("bulkDeleteTransactionRequests()")
      sender ! (mapper.bulkDeleteTransactionRequests())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

