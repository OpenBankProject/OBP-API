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

package code.taxresidence

import code.api.util.ErrorMessages
import code.customer.MappedCustomer
import code.util.{MappedUUID, MediumString}
import com.openbankproject.commons.model.TaxResidence
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedTaxResidenceProvider extends TaxResidenceProvider {
  
  override def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]] = Future {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id.map(customer => MappedTaxResidence.findAll(By(MappedTaxResidence.mCustomerId, customer.id.get)))
  }
  
  override def createTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]] = Future {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id match {
      case Full(customer) =>
        tryo(MappedTaxResidence.create.mCustomerId(customer.id.get).mDomain(domain).mTaxNumber(taxNumber).saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  
  override def deleteTaxResidence(taxResidenceId: String): Future[Box[Boolean]] = Future {
    MappedTaxResidence.find(By(MappedTaxResidence.mTaxResidenceId, taxResidenceId)) match {
      case Full(t) => Full(t.delete_!)
      case Empty   => Empty ?~! ErrorMessages.TaxResidenceNotFound
      case _       => Full(false)
    }
  }
}

class MappedTaxResidence extends TaxResidence with LongKeyedMapper[MappedTaxResidence] with IdPK with CreatedUpdated {

  def getSingleton = MappedTaxResidence

  object mCustomerId extends MappedLongForeignKey(this, MappedCustomer)
  object mTaxResidenceId extends MappedUUID(this)
  object mDomain extends MediumString(this)
  object mTaxNumber extends MediumString(this)

  override def customerId: String = mCustomerId.foreign.map(_.customerId).getOrElse(mCustomerId.get.toString)
  override def taxResidenceId: String = mTaxResidenceId.get
  override def domain: String = mDomain.get
  override def taxNumber: String = mTaxNumber.get

}

object MappedTaxResidence extends MappedTaxResidence with LongKeyedMetaMapper[MappedTaxResidence] {
  override def dbIndexes = UniqueIndex(mCustomerId, mDomain, mTaxNumber) :: super.dbIndexes
}
