package code.transactionStatusScheduler

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import code.transactionrequests.TransactionRequests
import code.util.Helper.MdcLoggable


import scala.concurrent.duration._


object TransactionStatusScheduler extends MdcLoggable {

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
    TransactionRequests.transactionRequestProvider.vend.updateAllPendingTransactionRequests
  }


}
