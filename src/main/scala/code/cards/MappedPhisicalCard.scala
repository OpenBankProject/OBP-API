package code.cards

import java.util.Date

import code.api.util.ErrorMessages
import code.model.dataAccess.MappedBankAccount
import code.model._
import code.views.Views
import code.views.Views._
import net.liftweb.mapper.{By, MappedString, _}
import net.liftweb.common.{Box, Full}
import net.liftweb.util.Helpers.tryo


/**
  * Created by markom on 11/10/16.
  */

object MappedPhysicalCardProvider extends PhysicalCardProvider {
  override def createOrUpdatePhysicalCard(bankCardNumber: String,
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

    val mappedBankAccountPrimaryKey: Long = MappedBankAccount
      .find(
        By(MappedBankAccount.bank, bankId),
        By(MappedBankAccount.theAccountId, accountId))
      .openOrThrowException(s"$accountId do not have Primary key, please contact admin, check the database! ").id.get
    
    def getPhysicalCard(bankId: BankId, bankCardNumber: String): Box[MappedPhysicalCard] = {
      MappedPhysicalCard.find(
        By(MappedPhysicalCard.mBankId, bankId.value),
        By(MappedPhysicalCard.mBankCardNumber, bankCardNumber)
      )
    }

    val r = replacement match {
      case Some(c) => CardReplacementInfo(requestedDate = c.requestedDate, reasonRequested = c.reasonRequested)
      case _       => CardReplacementInfo(requestedDate = null, reasonRequested = null)
    }
    val c = collected match {
      case Some(c) => CardCollectionInfo(date = c.date)
      case _       => CardCollectionInfo(date = null)
    }
    val p = posted match {
      case Some(c) => CardPostedInfo(date = c.date)
      case _       => CardPostedInfo(date = null)
    }
    
    //check the product existence and update or insert data
    val result = getPhysicalCard(BankId(bankId), bankCardNumber) match {
      case Full(mappedPhysicalCard) =>
        tryo {
          mappedPhysicalCard
            .mBankId(bankId)
            .mBankCardNumber(bankCardNumber)
            .mIssueNumber(nameOnCard)
            .mNameOnCard(issueNumber)
            .mSerialNumber(serialNumber)
            .mValidFrom(validFrom)
            .mExpires(expires)
            .mEnabled(enabled)
            .mCancelled(cancelled)
            .mOnHotList(onHotList)
            .mTechnology(technology)
            .mNetworks(networks.mkString(","))
            .mAllows(allows.mkString(","))
            .mReplacementDate(r.requestedDate)
            .mReplacementReason(r.reasonRequested.toString)
            .mCollected(c.date)
            .mPosted(c.date)
            .mAccount(mappedBankAccountPrimaryKey) // Card <-MappedLongForeignKey-> BankAccount, so need the primary key here.
            .saveMe()
        } ?~! ErrorMessages.UpdateCardError
      case _ =>
        tryo {
          MappedPhysicalCard.create
            .mBankId(bankId)
            .mBankCardNumber(bankCardNumber)
            .mIssueNumber(nameOnCard)
            .mNameOnCard(issueNumber)
            .mSerialNumber(serialNumber)
            .mValidFrom(validFrom)
            .mExpires(expires)
            .mEnabled(enabled)
            .mCancelled(cancelled)
            .mOnHotList(onHotList)
            .mTechnology(technology)
            .mNetworks(networks.mkString(","))
            .mAllows(allows.mkString(","))
            .mReplacementDate(r.requestedDate)
            .mReplacementReason(r.reasonRequested.toString)
            .mCollected(c.date)
            .mPosted(c.date)
            .mAccount(mappedBankAccountPrimaryKey) // Card <-MappedLongForeignKey-> BankAccount, so need the primary key here.
            .saveMe()
        } ?~! ErrorMessages.CreateCardError
    }
    result match {
      case Full(v) =>
        for(pinReset <- pinResets) {
          val pin = PinReset.create
            .mReplacementReason(pinReset.reasonRequested.toString)
            .mReplacementDate(pinReset.requestedDate)
            .card(v)
            .saveMe()
          v.mPinResets += pin
          v.save()
        }
      case _ => // There is no enough information to set foreign key
    }
    result
  }
  def getPhysicalCards(user: User) = {
    val accounts = views.vend.getPrivateBankAccounts(user)
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
      account <- views.vend.getPrivateBankAccounts(user, bank.bankId)
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