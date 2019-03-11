package code

import code.metadata.counterparties.Counterparties
import com.openbankproject.commons.model._

/**
  * in order to split some model and case class to commons module, and make the commons module is depended by api module, but
  * commons module not dependent api module, So some db related code is moved to XxxEx case class, and use implicit to combine them,
  * So commons module is clear an isolated.
  * e.g implicit method toBankEx:
  * Bank -> Bank + BankEx
  */
package object model {

  implicit def toBankEx(bank: Bank) = BankEx(bank)

  implicit def toBankAccountEx(bankAccount: BankAccount) = BankAccountEx(bankAccount)

  implicit def toCommentEx(comment: Comment) = CommentEx(comment)

  implicit def toUserEx(user: User) = UserEx(user)

  implicit def toViewEx(view: View) = ViewEx(view)

  implicit class CounterpartyEx(counterparty: Counterparty) {
    lazy val metadata: CounterpartyMetadata = Counterparties.counterparties.vend.getOrCreateMetadata(
      counterparty.thisBankId,
      counterparty.thisAccountId,
      counterparty.counterpartyId,
      counterparty.counterpartyName
    ).openOrThrowException("Can not getOrCreateMetadata !")
  }
}
