package code.scheduler

import code.actorsystem.ObpLookupSystem
import code.api.Constant
import code.api.util.APIUtil.generateUUID
import code.api.util.APIUtil
import code.nonce.Nonces
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.{By, By_<=}

import java.util.concurrent.TimeUnit
import java.util.Date
import scala.concurrent.duration._
import code.token.Tokens


object DataBaseCleanerScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpLookupSystem.obpLookupSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val oneDayInMillis: Long = 86400000
  //in scala DataBaseCleanerScheduler.getClass.getSimpleName ==> DataBaseCleanerScheduler$
  private val jobName = DataBaseCleanerScheduler.getClass.getSimpleName.replace("$", "")
  private val apiInstanceId = Constant.ApiInstanceId

  def start(intervalInSeconds: Long): Unit = {
    logger.info(s"Hello from $jobName.start")

    logger.info(s"--------- Clean up Jobs ---------")
    logger.info(s"Delete all Jobs created by api_instance_id=$apiInstanceId")
    JobScheduler.findAll(By(JobScheduler.Name, apiInstanceId)).map { i =>
      logger.info(s"Job name: ${i.name}, Date: ${i.createdAt}")
      i
    }.map(_.delete_!)
    logger.info(s"Delete all Jobs older than 5 days")
    val fiveDaysAgo: Date = new Date(new Date().getTime - (oneDayInMillis * 5))
    JobScheduler.findAll(By_<=(JobScheduler.createdAt, fiveDaysAgo)).map { i =>
      logger.info(s"Job name: ${i.name}, Date: ${i.createdAt}, api_instance_id: ${apiInstanceId}")
      i
    }.map(_.delete_!)
    
    scheduler.schedule(
      initialDelay = Duration(intervalInSeconds, TimeUnit.SECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          JobScheduler.find(By(JobScheduler.Name, jobName)) match {
            case Full(job) => // There is an ongoing/hanging job
              logger.info(s"Cannot start $jobName.start.run due to ongoing job. Job ID: ${job.JobId}")
            case _ => // Start a new job
              val uniqueId = generateUUID()
              val job = JobScheduler.create
                .JobId(uniqueId)
                .Name(jobName)
                .ApiInstanceId(apiInstanceId)
                .saveMe()
              logger.info(s"Starting $jobName.Job ID: $uniqueId")
              deleteExpiredTokensAndNonces()
              JobScheduler.delete_!(job) // Allow future jobs
              logger.info(s"End of $jobName.Job ID: $uniqueId")
          }
        } 
      }
    )
    logger.info(s"Bye from $jobName.start")
  }

  def deleteExpiredTokensAndNonces() = {
    //looks for expired tokens and nonces and deletes them
    val currentDate = new Date()
    //delete expired tokens and nonces
    Tokens.tokens.vend.deleteExpiredTokens(currentDate)
    Nonces.nonces.vend.deleteExpiredNonces(currentDate)
  }


}

