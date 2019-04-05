package code

import code.metadata.comments.Comments
import code.metadata.counterparties.Counterparties
import code.metadata.narrative.Narrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import com.openbankproject.commons.model._

/**
  * in order to split some model and case class to commons module, and make the commons module is depended by api module, but
  * commons module not dependent api module, So some db related code is moved to XxxEx case class, and use implicit to combine them,
  * So commons module is clear an isolated.
  * e.g implicit method toBankEx:
  * Bank -> Bank + BankEx
  */
package object model {

  implicit def toBankExtended(bank: Bank) = BankExtended(bank)

  implicit def toBankAccountExtended(bankAccount: BankAccount) = BankAccountExtended(bankAccount)

  implicit def toCommentExtended(comment: Comment) = CommentExtended(comment)

  implicit def toUserExtended(user: User) = UserExtended(user)

  implicit def toViewExtended(view: View) = ViewExtended(view)

  implicit class CounterpartyExtended(counterparty: Counterparty) {
    lazy val metadata: CounterpartyMetadata = Counterparties.counterparties.vend.getOrCreateMetadata(
      counterparty.thisBankId,
      counterparty.thisAccountId,
      counterparty.counterpartyId,
      counterparty.counterpartyName
    ).openOrThrowException("Can not getOrCreateMetadata !")
  }

  implicit class TransactionExtended(transaction: Transaction) {

    private[this] val bankId = transaction.bankId
    private[this] val accountId = transaction.accountId
    private[this] val id = transaction.id
    /**
      * The metadata is set up using dependency injection. If you want to, e.g. override the Comments implementation
      * for a particular scope, use Comments.comments.doWith(NewCommentsImplementation extends Comments{}){
      *   //code in here will use NewCommentsImplementation (e.g. val t = new Transaction(...) will result in Comments.comments.vend
      *   // return NewCommentsImplementation here below)
      * }
      *
      * If you want to change the current default implementation, you would change the buildOne function in Comments to
      * return a different value
      *
      */
    val metadata : TransactionMetadata = new TransactionMetadata(
      Narrative.narrative.vend.getNarrative(bankId, accountId, id) _,
      Narrative.narrative.vend.setNarrative(bankId, accountId, id) _,
      Comments.comments.vend.getComments(bankId, accountId, id) _,
      Comments.comments.vend.addComment(bankId, accountId, id) _,
      Comments.comments.vend.deleteComment(bankId, accountId, id) _,
      Tags.tags.vend.getTags(bankId, accountId, id) _,
      Tags.tags.vend.addTag(bankId, accountId, id) _,
      Tags.tags.vend.deleteTag(bankId, accountId, id) _,
      TransactionImages.transactionImages.vend.getImagesForTransaction(bankId, accountId, id) _,
      TransactionImages.transactionImages.vend.addTransactionImage(bankId, accountId, id) _,
      TransactionImages.transactionImages.vend.deleteTransactionImage(bankId, accountId, id) _,
      WhereTags.whereTags.vend.getWhereTagForTransaction(bankId, accountId, id) _,
      WhereTags.whereTags.vend.addWhereTag(bankId, accountId, id) _,
      WhereTags.whereTags.vend.deleteWhereTag(bankId, accountId, id) _
    )
  }
}
