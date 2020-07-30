package code.transactionStatusScheduler

import java.util.concurrent.TimeUnit

import code.actorsystem.ObpLookupSystem
import code.transactionrequests.TransactionRequests
import code.util.Helper.MdcLoggable

import scala.concurrent.duration._


object TransactionStatusScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpLookupSystem.obpLookupSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler

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
