/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.accountapplication

import java.util.Date

import code.api.util.ErrorMessages
import code.util.MappedUUID
import com.openbankproject.commons.model.{AccountApplication, ProductCode}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedAccountApplicationProvider extends AccountApplicationProvider {

  /** The status every application starts in, and the only status a decision may be taken from. */
  private val RequestedStatus = "REQUESTED"

  override def getAll(): Future[Box[List[AccountApplication]]] = Future {
    tryo{MappedAccountApplication.findAll()}
  }

  override def getById(accountApplicationId: String): Future[Box[AccountApplication]] = Future {
    MappedAccountApplication.find(By(MappedAccountApplication.mAccountApplicationId, accountApplicationId))
  }

  override def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String]): Future[Box[AccountApplication]] =
    Future {
      tryo {
        MappedAccountApplication.create.mCode(productCode.value).mUserId(userId.orNull).mCustomerId(customerId.orNull).mStatus(RequestedStatus).saveMe()
      }
  }

  override def updateStatus(accountApplicationId:String, status: String): Future[Box[AccountApplication]] = 
    Future{
      MappedAccountApplication.find(By(MappedAccountApplication.mAccountApplicationId, accountApplicationId))
       match {
        case Full(accountApplication) if(accountApplication.status == "ACCEPTED") =>
          Failure(s"${ErrorMessages.AccountApplicationAlreadyAccepted} Current Account-Application-Id($accountApplicationId)")
        case Full(accountApplication)  =>
          // The decision is one-shot: it may only be taken from REQUESTED. Guarding on the fixed
          // initial status rather than the one just loaded is what makes that hold. A guard built
          // from the loaded status matches whatever a preceding decision wrote, so a REJECTED
          // application could be re-decided as ACCEPTED — and the ACCEPTED branch of the endpoint
          // opens a bank account, so that overwrite is not recoverable.
          val rows = code.bankconnectors.DoobieBusinessStatusQueries.conditionalAccountApplicationStatus(
            accountApplication.id.get, RequestedStatus, status)
          if (rows == 1) MappedAccountApplication.find(By(MappedAccountApplication.mAccountApplicationId, accountApplicationId))
          // 0 rows means the application left REQUESTED — either a concurrent decision won the race
          // or one was already recorded. Use the generic update-failure code: the winner may have
          // written any status, so the "already accepted" message would be misleading.
          else Failure(s"${ErrorMessages.UpdateAccountApplicationStatusError} The account application is no longer in $RequestedStatus status. Current Account-Application-Id($accountApplicationId)")
        case Empty  => Failure(s"${ErrorMessages.AccountApplicationNotFound} Current Account-Application-Id($accountApplicationId)") 
        case _  => Failure(ErrorMessages.UnknownError) 
      }    
    }
  
}

class MappedAccountApplication extends AccountApplication with LongKeyedMapper[MappedAccountApplication] with IdPK with CreatedUpdated {

  def getSingleton = MappedAccountApplication

  object mAccountApplicationId extends MappedUUID(this)
  object mCode extends MappedString(this, 50)
  object mCustomerId extends MappedUUID(this)
  object mUserId extends MappedUUID(this) //resourceUser
  object mStatus extends MappedString(this, 255)

  override def accountApplicationId: String = mAccountApplicationId.get

  override def productCode: ProductCode = ProductCode(mCode.get)
  override def userId: String = mUserId.get
  override def customerId: String = mCustomerId.get
  override def dateOfApplication: Date = createdAt.get
  override def status: String = mStatus.get

}

object MappedAccountApplication extends MappedAccountApplication with LongKeyedMetaMapper[MappedAccountApplication] {
  override def dbIndexes = UniqueIndex(mAccountApplicationId) :: super.dbIndexes
}
