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

package code.metadata.transactionimages

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(() => buildOne) {}

  def buildOne: TransactionImages = MapperTransactionImages
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage]
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: UserPrimaryKey, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean]
  
  def bulkDeleteImagesOnTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId) : Boolean

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean
  
}
