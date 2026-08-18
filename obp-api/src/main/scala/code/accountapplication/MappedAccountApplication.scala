package code.accountapplication

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountApplication, ProductCode}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/**
 * An application to open an account.
 *
 * `id` is carried because the status transition is guarded by a conditional UPDATE that keys off
 * the numeric primary key rather than the public application id.
 *
 * `userId` and `customerId` come from Option[String] via orNull and genuinely hold NULL, so they
 * are read as Option and surfaced as null — the trait types them as bare Strings.
 */
case class MappedAccountApplication(
  id: Long,
  accountApplicationId: String,
  productCode: ProductCode,
  userId: String,
  customerId: String,
  status: String,
  dateOfApplication: Date
) extends AccountApplication

object MappedAccountApplication {

  private val selectColumns =
    fr"""SELECT id, maccountapplicationid, mcode, muserid, mcustomerid, mstatus, createdat
         FROM mappedaccountapplication"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp])

  private def fromRow(row: Row): MappedAccountApplication = row match {
    case (id, accountApplicationId, code, userId, customerId, status, createdAt) =>
      MappedAccountApplication(id, accountApplicationId.orNull, ProductCode(code.orNull),
        userId.orNull, customerId.orNull, status.orNull, createdAt.orNull)
  }

  private def query(condition: Fragment): List[MappedAccountApplication] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAll(): List[MappedAccountApplication] = query(fr"ORDER BY id ASC")

  def findById(accountApplicationId: String): Box[MappedAccountApplication] =
    query(fr"WHERE maccountapplicationid = $accountApplicationId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def insert(productCode: ProductCode, userId: Option[String], customerId: Option[String],
             status: String): MappedAccountApplication = {
    val accountApplicationId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedaccountapplication
            (maccountapplicationid, mcode, muserid, mcustomerid, mstatus, createdat, updatedat)
            VALUES ($accountApplicationId, ${productCode.value}, $userId, $customerId, $status,
             $now, $now)"""
        .update.run)
    findById(accountApplicationId)
      .openOrThrowException("the account application just inserted must be readable")
  }

  def deleteByCustomerId(customerId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedaccountapplication WHERE mcustomerid = $customerId".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountapplication".update.run)
    ()
  }
}

object MappedAccountApplicationProvider extends AccountApplicationProvider {

  /** The status every application starts in, and the only status a decision may be taken from. */
  private val RequestedStatus = "REQUESTED"

  override def getAll(): Future[Box[List[AccountApplication]]] = Future {
    tryo(MappedAccountApplication.findAll())
  }

  override def getById(accountApplicationId: String): Future[Box[AccountApplication]] = Future {
    MappedAccountApplication.findById(accountApplicationId)
  }

  override def createAccountApplication(productCode: ProductCode, userId: Option[String],
                                        customerId: Option[String]): Future[Box[AccountApplication]] =
    Future {
      tryo(MappedAccountApplication.insert(productCode, userId, customerId, RequestedStatus))
    }

  override def updateStatus(accountApplicationId: String, status: String): Future[Box[AccountApplication]] =
    Future {
      MappedAccountApplication.findById(accountApplicationId) match {
        case Full(accountApplication) if accountApplication.status == "ACCEPTED" =>
          Failure(s"${ErrorMessages.AccountApplicationAlreadyAccepted} Current Account-Application-Id($accountApplicationId)")
        case Full(accountApplication) =>
          // The decision is one-shot: it may only be taken from REQUESTED. Guarding on the fixed
          // initial status rather than the one just loaded is what makes that hold. A guard built
          // from the loaded status matches whatever a preceding decision wrote, so a REJECTED
          // application could be re-decided as ACCEPTED — and the ACCEPTED branch of the endpoint
          // opens a bank account, so that overwrite is not recoverable.
          val rows = code.bankconnectors.DoobieBusinessStatusQueries.conditionalAccountApplicationStatus(
            accountApplication.id, RequestedStatus, status)
          if (rows == 1) MappedAccountApplication.findById(accountApplicationId)
          // 0 rows means the application left REQUESTED — either a concurrent decision won the race
          // or one was already recorded. Use the generic update-failure code: the winner may have
          // written any status, so the "already accepted" message would be misleading.
          else Failure(s"${ErrorMessages.UpdateAccountApplicationStatusError} The account application is no longer in $RequestedStatus status. Current Account-Application-Id($accountApplicationId)")
        case Empty => Failure(s"${ErrorMessages.AccountApplicationNotFound} Current Account-Application-Id($accountApplicationId)")
        case _ => Failure(ErrorMessages.UnknownError)
      }
    }
}
