package code.transactionStatusScheduler

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem}
import akka.event.Logging
import code.transactionrequests.{MappedTransactionRequest, TransactionRequests}
import net.liftweb.common.{Loggable, Logger}
import net.liftweb.mapper.By

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
        def run(): Unit = updateAllPendingTransactions
      }
    )
  }

  def updateAllPendingTransactions = {
    val transactions = MappedTransactionRequest.find(By(MappedTransactionRequest.mStatus, TransactionRequests.STATUS_PENDING))
    logger.info("Updating status of all pending transactions: ")
    transactions.map{ t =>
      println(s"${t.toTransactionRequest}")
    }

  }


}
