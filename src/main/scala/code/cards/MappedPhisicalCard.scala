package code.cards

import java.util.Date

import code.model.dataAccess.MappedBankAccount
import code.model._
import code.views.Views
import net.liftweb.mapper.{MappedString, _}
import net.liftweb.common.{Box, Empty, Full}


/**
  * Created by markom on 11/10/16.
  */

object MappedPhysicalCardProvider extends PhysicalCardProvider {
  def AddPhysicalCard(bankCardNumber: String,
                      nameOnCard: String,
                      issueNumber: String,
                      serialNumber: String,
                      validFrom: Date,
                      expires: Date,
                      enabled: Boolean,
                      cancelled: Boolean,
                      onHotList: Boolean,
                      technology: String,
                      networks: List[String],
                      allows: List[String],
                      accountId: String,
                      bankId: String,
                      replacement: Option[CardReplacementInfo],
                      pinResets: List[PinResetInfo],
                      collected: Option[CardCollectionInfo],
                      posted: Option[CardPostedInfo]
                     ): Box[MappedPhysicalCard] = {

    val accountId1 = MappedBankAccount.find(By(MappedBankAccount.bank, bankId), By(MappedBankAccount.theAccountId, accountId)) match {
      case Full(a) => a.id.get
      case _ => 0
    }
      Some(
      MappedPhysicalCard.create
        .mBankCardNumber(bankCardNumber)
        .mIssueNumber(nameOnCard)
        .mNameOnCard(issueNumber)
        .mSerialNumber(serialNumber)
        .mValidFrom(validFrom)
        .mExpires(expires)
        .mEnabled(enabled)
        .mCancelled(cancelled)
        .mOnHotList(onHotList)
        .mAllows(allows.mkString(","))
        .mAccount(accountId1)
        .saveMe()
        )
  }
  def getPhysicalCards(user: User) = {
    val accounts = Views.views.vend.getAllAccountsUserCanSee(Full(user))
    val allCards: List[MappedPhysicalCard] = MappedPhysicalCard.findAll()
    val cards = for {
      account <- accounts
      card <- allCards if account.accountId.value == card.account.accountId.value
    } yield {
      card
    }
    cards
  }

  def getPhysicalCardsForBank(bank: Bank, user: User) = {
    val allCards: List[MappedPhysicalCard] = MappedPhysicalCard.findAll()
    val cards = for {
      account <- bank.accounts(Full(user))
      card <- allCards if account.accountId.value == card.account.accountId.value
    } yield {
       card
    }
    cards
  }

}

class MappedPhysicalCard extends PhysicalCardTrait with LongKeyedMapper[MappedPhysicalCard] with IdPK with OneToMany[Long, MappedPhysicalCard] {
  def getSingleton = MappedPhysicalCard

  object mBankId extends MappedString(this, 50)
  object mBankCardNumber extends MappedString(this, 50)
  object mNameOnCard extends MappedString(this, 128)
  object mIssueNumber extends MappedString(this, 10)
  object mSerialNumber extends MappedString(this, 50)
  object mValidFrom extends MappedDateTime(this)
  object mExpires extends MappedDateTime(this)
  object mEnabled extends MappedBoolean(this)
  object mCancelled extends MappedBoolean(this)
  object mOnHotList extends MappedBoolean(this)
  object mTechnology extends MappedString(this, 255)
  object mNetworks extends MappedString(this, 255)
  object mAllows extends MappedString(this, 255)
  object mAccount extends MappedLongForeignKey(this, MappedBankAccount)
  object mReplacementDate extends MappedDateTime(this)
  object mReplacementReason extends MappedString(this, 255)
  object mPinResets extends MappedOneToMany(PinReset, PinReset.card, OrderBy(PinReset.id, Ascending))
  object mCollected extends MappedDateTime(this)
  object mPosted extends MappedDateTime(this)

  def bankId: String = mBankId.get
  def bankCardNumber: String = mBankCardNumber.get
  def nameOnCard: String = mNameOnCard.get
  def issueNumber: String = mIssueNumber.get
  def serialNumber: String = mSerialNumber.get
  def validFrom: Date = mValidFrom.get
  def expires: Date = mExpires.get
  def enabled: Boolean = mEnabled.get
  def cancelled: Boolean = mCancelled.get
  def onHotList: Boolean = mOnHotList.get
  def technology: String = mTechnology.get
  def networks: List[String] = mNetworks.get.split(",").toList
  def allows: List[code.model.CardAction] = Option(mAllows.get) match {
    case Some(x) if (!x.isEmpty) => x.split(",").toList.map(code.model.CardAction.valueOf((_)))
    case _ => List()
  }
  def account = mAccount.obj match {
    case Full(x) => x
    case _ => throw new Exception ("Account is mandatory")
  }
  def replacement: Option[CardReplacementInfo] = Option(mReplacementDate.get) match {
    case Some(date) => Option(mReplacementReason.get) match {
      case Some(reason) => Some(CardReplacementInfo(date, CardReplacementReason.valueOf(reason)))
      case _ => None
    }
    case _ => None
  }
  def pinResets: List[code.model.PinResetInfo] = mPinResets.map(a => PinResetInfo(a.mReplacementDate.get, PinResetReason.valueOf(a.mReplacementReason.get))).toList
  def collected: Option[CardCollectionInfo] = Option(mCollected.get) match {
    case Some(x) => Some(CardCollectionInfo(x))
    case _ => None
  }
  def posted: Option[CardPostedInfo] = Option(mPosted.get)  match {
    case Some(x) => Some(CardPostedInfo(x))
    case _ => None
  }

}

object MappedPhysicalCard extends MappedPhysicalCard with LongKeyedMetaMapper[MappedPhysicalCard] {
  override def dbIndexes = UniqueIndex(mBankId, mBankCardNumber) :: super.dbIndexes
}



class PinReset extends LongKeyedMapper[PinReset] with IdPK {
  def getSingleton = PinReset

  object card extends MappedLongForeignKey(this, MappedPhysicalCard)
  object mReplacementDate extends MappedDateTime(this)
  object mReplacementReason extends MappedString(this, 255)
}
object PinReset extends PinReset with LongKeyedMetaMapper[PinReset]{}


class CardAction extends LongKeyedMapper[CardAction] with IdPK {
  def getSingleton = CardAction

  object post extends MappedLongForeignKey(this, MappedPhysicalCard)
  object cardAction extends MappedString(this, 140)
}
object CardAction extends CardAction with LongKeyedMetaMapper[CardAction]{}