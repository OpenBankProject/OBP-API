package code.transactionStatusScheduler

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem}
import akka.event.Logging
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
    transactionRequests.map{ tr =>
      val status = updatePendingTransactionRequest(tr)
      println(s"---> updated ${tr.toTransactionRequest} status: ${status}")
    }
  }

  def updatePendingTransactionRequest(tr: MappedTransactionRequest): Box[Boolean]  = {
    Full(false)
  }


}
