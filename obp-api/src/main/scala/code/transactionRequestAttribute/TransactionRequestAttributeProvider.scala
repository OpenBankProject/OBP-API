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

package code.transactionRequestAttribute

import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import com.openbankproject.commons.model.{BankId, TransactionRequestAttributeJsonV400, TransactionRequestAttributeTrait, TransactionRequestId, ViewId}
import net.liftweb.common.{Box, Logger}
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.Future

trait TransactionRequestAttributeProvider extends MdcLoggable {

  def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]]

  def getTransactionRequestAttributes(bankId: BankId,
                                      transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]]

  def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                     transactionRequestId: TransactionRequestId,
                                                     viewId: ViewId): Future[Box[List[TransactionRequestAttributeTrait]]]

  def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttributeTrait]]

  def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[String]]]

  def getByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]]

  def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                transactionRequestId: TransactionRequestId,
                                                transactionRequestAttributeId: Option[String],
                                                name: String,
                                                attributeType: TransactionRequestAttributeType.Value,
                                                value: String): Future[Box[TransactionRequestAttributeTrait]]

  def createTransactionRequestAttributes(bankId: BankId,
                                         transactionRequestId: TransactionRequestId,
                                         transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
                                         isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]]

  def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]]

}
