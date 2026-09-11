package code.scheduler

import code.actorsystem.ObpActorSystem
import code.api.Constant
import code.api.util.APIUtil.generateUUID
import code.api.util.APIUtil
import code.nonce.Nonces
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

import java.util.concurrent.TimeUnit
import java.util.Date
import scala.concurrent.duration._
import code.token.Tokens


object DataBaseCleanerScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
  implicit lazy val executor: scala.concurrent.ExecutionContextExecutor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val oneDayInMillis: Long = 86400000
  //in scala DataBaseCleanerScheduler.getClass.getSimpleName ==> DataBaseCleanerScheduler$
  private val jobName = DataBaseCleanerScheduler.getClass.getSimpleName.replace("$", "")
  private val apiInstanceId = Constant.ApiInstanceId

  def start(intervalInSeconds: Long): Unit = {
    logger.info(s"Hello from $jobName.start")

    logger.info(s"--------- Clean up Jobs ---------")
    logger.info(s"Delete all Jobs created by api_instance_id=$apiInstanceId")
    // Matches Name against apiInstanceId, which never hits: lock rows store Name=jobName and
    // ApiInstanceId=apiInstanceId. MetricsArchiveScheduler carries a comment describing this exact
    // mismatch and was fixed to key on ApiInstanceId; this scheduler was not. Preserved verbatim -
    // correcting it here would change self-heal-on-redeploy behaviour under cover of a storage swap.
    JobScheduler.findAllByName(apiInstanceId).map { i =>
      logger.info(s"Job name: ${i.name}, Date: ${i.createdAt}")
      i
    }.map(JobScheduler.delete)
    logger.info(s"Delete all Jobs older than 5 days")
    val fiveDaysAgo: Date = new Date(new Date().getTime - (oneDayInMillis * 5))
    JobScheduler.findAllCreatedOnOrBefore(fiveDaysAgo).map { i =>
      logger.info(s"Job name: ${i.name}, Date: ${i.createdAt}, api_instance_id: ${apiInstanceId}")
      i
    }.map(JobScheduler.delete)
    
    scheduler.schedule(
      initialDelay = Duration(intervalInSeconds, TimeUnit.SECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          JobScheduler.findByName(jobName) match {
            case Full(job) => // There is an ongoing/hanging job
              logger.info(s"Cannot start $jobName.start.run due to ongoing job. Job ID: ${job.jobId}")
            case _ => // Start a new job
              val uniqueId = generateUUID()
              val job = JobScheduler.createJob(uniqueId, jobName, apiInstanceId)
              logger.info(s"Starting $jobName.Job ID: $uniqueId")
              deleteExpiredTokensAndNonces()
              JobScheduler.delete(job) // Allow future jobs
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

