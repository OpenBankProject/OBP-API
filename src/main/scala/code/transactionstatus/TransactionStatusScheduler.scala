package code.transactionStatusScheduler

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem}
import akka.event.Logging
import code.bankconnectors.Connector
import code.model.{TransactionRequestId, TransactionRequestStatus}
import code.transactionrequests.MappedTransactionRequest
import code.transactionrequests.TransactionRequests
import code.transactionrequests.TransactionRequests._
import net.liftweb.common.{Loggable, Logger}
import net.liftweb.mapper.By
import net.liftweb.common.{Box, Empty, Failure, Full, Loggable}

import scala.concurrent.duration._


object TransactionStatusScheduler extends Loggable {

  val actorSystem = ActorSystem()
  implicit val executor = actorSystem.dispatcher
  val scheduler = actorSystem.scheduler

  def start(interval: Long): Unit = {
    scheduler.schedule(
      initialDelay = Duration(interval, TimeUnit.SECONDS),
      interval = Duration(interval, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = updateAllPendingTransactionRequests
      }
    )
  }

  def updateAllPendingTransactionRequests = {
    val transactionRequests = MappedTransactionRequest.find(By(MappedTransactionRequest.mStatus, TransactionRequests.STATUS_PENDING))
    logger.info("Updating status of all pending transactions: ")
    val statuses = Connector.connector.vend.getTransactionRequestStatuses
    transactionRequests.map{ tr =>
      for {
        transactionRequest <- tr.toTransactionRequest
        if (statuses.exists(_ == transactionRequest.id -> "APVD"))
      } yield {
	      tr.updateStatus(TransactionRequests.STATUS_COMPLETED)
        logger.info(s"updated ${transactionRequest.id} status: ${TransactionRequests.STATUS_COMPLETED}")
      }
    }
  }


}
