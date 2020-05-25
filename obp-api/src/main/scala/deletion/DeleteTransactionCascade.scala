package deletion

import code.metadata.comments.Comments
import code.metadata.narrative.Narrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.transaction.MappedTransaction
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}
import net.liftweb.db.DB
import net.liftweb.mapper.{By}
import net.liftweb.util.DefaultConnectionIdentifier

object DeleteTransactionCascade {
  def delete(bankId: BankId, accountId: AccountId, id: TransactionId): Boolean = {
    DB.use(DefaultConnectionIdentifier){_ =>
      val narrative = Narrative.narrative.vend.setNarrative(bankId, accountId, id)("")
      val comments = Comments.comments.vend.bulkDeleteCommentsOnTransaction(bankId, accountId, id)
      val tags = Tags.tags.vend.bulkDeleteTagsOnTransaction(bankId, accountId, id)
      val images = TransactionImages.transactionImages.vend.bulkDeleteImagesOnTransaction(bankId, accountId, id)
      val whereTags = WhereTags.whereTags.vend.bulkDeleteWhereTagsOnTransaction(bankId, accountId, id)
      val transaction = MappedTransaction.bulkDelete_!!(By(MappedTransaction.transactionId, id.value))
      val doneTasks = List(narrative, comments, tags, images, whereTags, transaction, false)
      val deleted = doneTasks.forall(_ == true)
      if(!deleted)
        DB.rollback(DefaultConnectionIdentifier)
      deleted
    }
  }
}
