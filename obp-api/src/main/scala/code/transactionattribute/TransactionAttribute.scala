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

package code.transactionattribute

/* For TransactionAttribute */

import code.api.util.APIUtil
import com.openbankproject.commons.model.enums.TransactionAttributeType
import com.openbankproject.commons.model.{BankId, TransactionAttribute, TransactionId, ViewId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.Future

object TransactionAttributeX extends SimpleInjector {

  val transactionAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: TransactionAttributeProvider = MappedTransactionAttributeProvider

  // Helper to get the count out of an option
  def countOfTransactionAttribute(listOpt: Option[List[TransactionAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait TransactionAttributeProvider extends MdcLoggable {

  def getTransactionAttributesFromProvider(transactionId: TransactionId): Future[Box[List[TransactionAttribute]]]
  def getTransactionAttributes(bankId: BankId,
                                    transactionId: TransactionId): Future[Box[List[TransactionAttribute]]]
  def getTransactionAttributesCanBeSeenOnView(bankId: BankId,
                                              transactionId: TransactionId,
                                              viewId: ViewId): Future[Box[List[TransactionAttribute]]]
  def getTransactionsAttributesCanBeSeenOnView(bankId: BankId,
                                               transactionIds: List[TransactionId],
                                               viewId: ViewId): Future[Box[List[TransactionAttribute]]]
  def getTransactionAttributeById(transactionAttributeId: String): Future[Box[TransactionAttribute]]

  def getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]

  def createOrUpdateTransactionAttribute(bankId: BankId,
                                     transactionId: TransactionId,
                                     transactionAttributeId: Option[String],
                                     name: String,
                                     attributeType: TransactionAttributeType.Value,
                                     value: String): Future[Box[TransactionAttribute]]

  def createTransactionAttributes(bankId: BankId,
                              transactionId: TransactionId,
                              transactionAttributes: List[TransactionAttribute]): Future[Box[List[TransactionAttribute]]]

  def deleteTransactionAttribute(transactionAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}
