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

package deletion

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.metadata.comments.Comments
import code.metadata.narrative.Narrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.transaction.MappedTransaction
import code.transactionattribute.MappedTransactionAttribute
import code.transactionrequests.MappedTransactionRequestProvider
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}

object DeleteTransactionCascade {
  def delete(bankId: BankId, accountId: AccountId, id: TransactionId): Boolean = {
    val narrative = Narrative.narrative.vend.bulkDeleteNarrativeOnTransaction(bankId, accountId, id)
    val comments = Comments.comments.vend.bulkDeleteCommentsOnTransaction(bankId, accountId, id)
    val tags = Tags.tags.vend.bulkDeleteTagsOnTransaction(bankId, accountId, id)
    val images = TransactionImages.transactionImages.vend.bulkDeleteImagesOnTransaction(bankId, accountId, id)
    val whereTags = WhereTags.whereTags.vend.bulkDeleteWhereTagsOnTransaction(bankId, accountId, id)
    val transactionAttribute = deleteTransactionAttribute(bankId, id)
    val transactionRequest = MappedTransactionRequestProvider.bulkDeleteTransactionRequestsByTransactionId(id)
    val transaction = MappedTransaction.bulkDelete_!!(By(MappedTransaction.transactionId, id.value))
    val doneTasks = List(narrative, comments, tags, images, whereTags, transactionAttribute, transactionRequest, transaction)
    doneTasks.forall(_ == true)
  }
  
  def atomicDelete(bankId: BankId, accountId: AccountId, id: TransactionId): Box[Boolean] = databaseAtomicTask {
    delete(bankId, accountId, id) match {
      case true =>
        Full(true)
      case false =>
        DB.rollback(DefaultConnectionIdentifier)
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }
  
  private def deleteTransactionAttribute(bankId: BankId, id: TransactionId): Boolean = {
    MappedTransactionAttribute.bulkDelete_!!(
      By(MappedTransactionAttribute.mBankId, bankId.value),
      By(MappedTransactionAttribute.mTransactionId, id.value)
    )
  }
  
}
