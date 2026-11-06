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

package code.CustomerDependants

import java.util.Date

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CustomerDependant
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

import scala.collection.immutable.List

/**
 * A dependant's date of birth, hanging off a customer.
 *
 * `customerKey` is MAPPEDCUSTOMER's numeric primary key, not the public customer_id — the callers
 * already pass that key in, which is why it appears in the signatures below unchanged.
 */
case class MappedCustomerDependant(
  customerKey: Long,
  dateOfBirth: Date
)

object MappedCustomerDependant {

  private val selectColumns = fr"SELECT mcustomer, mdateofbirth FROM mappedcustomerdependant"

  private def query(condition: Fragment): List[MappedCustomerDependant] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[(Option[Long], Option[java.sql.Timestamp])].to[List])
      .map { case (customerKey, dateOfBirth) => MappedCustomerDependant(customerKey.getOrElse(0L), dateOfBirth.orNull) }

  def insert(customerKey: Long, dateOfBirth: Date): MappedCustomerDependant = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcustomerdependant (mcustomer, mdateofbirth)
            VALUES ($customerKey, ${new java.sql.Timestamp(dateOfBirth.getTime)})"""
        .update.run)
    MappedCustomerDependant(customerKey, dateOfBirth)
  }

  def findAllByCustomerKey(customerKey: Long): List[MappedCustomerDependant] =
    query(fr"WHERE mcustomer = $customerKey ORDER BY id ASC")

  def deleteByCustomerKey(customerKey: Long): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcustomerdependant WHERE mcustomer = $customerKey".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomerdependant".update.run)
    ()
  }
}

object MappedCustomerDependants extends CustomerDependants with MdcLoggable {

  def createCustomerDependants(mapperCustomerPrimaryKey: Long,
                               customerDependants: List[CustomerDependant]): List[MappedCustomerDependant] =
    customerDependants.map(d => MappedCustomerDependant.insert(mapperCustomerPrimaryKey, d.dateOfBirth))

  def getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long): List[MappedCustomerDependant] =
    MappedCustomerDependant.findAllByCustomerKey(mapperCustomerPrimaryKey)
}
