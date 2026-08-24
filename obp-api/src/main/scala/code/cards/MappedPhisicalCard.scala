package code.cards

import java.util.Date

import code.api.util._
import code.model._
import code.model.dataAccess.MappedBankAccount
import code.views.Views._
import com.openbankproject.commons.model.{CardAction => CardActionType, _}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

/**
 * A physical card issued against an account.
 *
 * `accountKey` is MAPPEDBANKACCOUNT's numeric primary key, not the public account_id. `account`
 * resolves it and throws when it does not resolve, because the trait types account as a bare
 * BankAccount with no absent case — the same behaviour the Lift foreign key had.
 *
 * `networks` and `allows` are both comma-joined lists in one column but are NOT parsed the same
 * way: allows filters empties out, networks does not, so an empty networks column yields
 * List("") rather than Nil. That asymmetry is pre-existing and visible to callers, so it is
 * preserved rather than tidied.
 *
 * `cvv` and `brand` are always Some, including when the column is empty — the accessors wrap
 * unconditionally.
 */
case class MappedPhysicalCard(
  cardId: String,
  bankId: String,
  bankCardNumber: String,
  cardType: String,
  nameOnCard: String,
  issueNumber: String,
  serialNumber: String,
  validFrom: Date,
  expires: Date,
  enabled: Boolean,
  cancelled: Boolean,
  onHotList: Boolean,
  technology: String,
  private val networksRaw: String,
  private val allowsRaw: String,
  accountKey: Long,
  private val replacementDate: Option[Date],
  private val replacementReason: Option[String],
  private val collectedDate: Option[Date],
  private val postedDate: Option[Date],
  customerId: String,
  private val cvvRaw: String,
  private val brandRaw: String,
  private[cards] val cardKey: Long
) extends PhysicalCardTrait {

  // Null-safe rather than null-collapsed, and the two are not the same answer. A NULL column - what
  // every row written before mnetworks was backfilled holds - means no networks were recorded, so
  // it reads as Nil. A column holding "" is a client that actually sent [""], and `"".split(",")`
  // is `Array("")`, so it reads back as it was sent. Collapsing NULL to "" upstream would merge the
  // two and silently change what an existing client gets back.
  override def networks: List[String] = Option(networksRaw).map(_.split(",").toList).getOrElse(Nil)

  override def allows: List[CardActionType] = Option(allowsRaw) match {
    case Some(x) if !x.isEmpty => x.split(",").toList.map(CardActionType.valueOf)
    case _ => List()
  }

  override def account: BankAccount =
    MappedBankAccount.findByPrimaryKey(accountKey)
      .openOr(throw new Exception("Account is mandatory"))

  override def replacement: Option[CardReplacementInfo] = replacementDate match {
    case Some(date) => replacementReason match {
      case Some(reason) => Some(CardReplacementInfo(date, CardReplacementReason.valueOf(reason)))
      case _ => None
    }
    case _ => None
  }

  override def pinResets: List[PinResetInfo] = PinReset.findAllByCardKey(cardKey)

  override def collected: Option[CardCollectionInfo] = collectedDate.map(CardCollectionInfo.apply)

  override def posted: Option[CardPostedInfo] = postedDate.map(CardPostedInfo.apply)

  // Option, not Some: mcvv and mbrand were added to the model years after the table existed and
  // Schemifier added them with no backfill, so every row written before that release holds SQL NULL.
  // `Some(null)`, which is what the old `Some(cvvRaw)` produced there, says the card has a CVV and
  // then hands out a null; None says what the column says. An actually-empty string still reads
  // back as Some("") - only the absent case changes.
  override def cvv: Option[String] = Option(cvvRaw)

  override def brand: Option[String] = Option(brandRaw)
}

object MappedPhysicalCard {

  private val selectColumns =
    fr"""SELECT mcardid, mbankid, mbankcardnumber, mcardtype, mnameoncard, missuenumber,
                mserialnumber, mvalidfrom, mexpires, menabled, mcancelled, monhotlist, mtechnology,
                mnetworks, mallows, maccount, mreplacementdate, mreplacementreason, mcollected,
                mposted, mcustomerid, mcvv, mbrand, id
         FROM mappedphysicalcard"""

  // Split in two because Scala tuples stop at 22 elements and this table has 24 columns to read.
  //
  // Only `id` is NOT NULL on this table. mcvv/mbrand in particular were added to the model years
  // after the table existed, and Schemifier added them with no backfill, so every card written
  // before that release holds SQL NULL there; maccount is a MappedLongForeignKey, which writes NULL
  // whenever it is undefined. Binding those bare made doobie raise NonNullableColumnRead and fail
  // the whole listing, so each column is read as Option and collapsed the way its Mapper field read
  // a NULL: MappedString -> null, MappedBoolean -> false, MappedLongForeignKey -> 0L,
  // MappedDateTime -> null.
  //
  // The four raw strings behind networks/allows/cvv/brand stay null rather than becoming "": their
  // accessors are null-safe, and the two values mean different things. "" is a client that sent an
  // empty value and must get it back unchanged; NULL is a column that was never written, and reads
  // as Nil / None. Collapsing here would merge them.
  private type RowHead = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[Boolean], Option[Boolean], Option[Boolean])
  private type RowTail = (Option[String], Option[String], Option[String], Option[Long],
    Option[java.sql.Timestamp], Option[String], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[String], Option[String], Option[String], Long)
  private type Row = (RowHead, RowTail)

  private def fromRow(row: Row): MappedPhysicalCard = row match {
    case ((cardId, bankId, bankCardNumber, cardType, nameOnCard, issueNumber, serialNumber,
           validFrom, expires, enabled, cancelled, onHotList),
          (technology, networks, allows, accountKey, replacementDate, replacementReason, collected,
           posted, customerId, cvv, brand, cardKey)) =>
      MappedPhysicalCard(cardId.orNull, bankId.orNull, bankCardNumber.orNull, cardType.orNull,
        nameOnCard.orNull, issueNumber.orNull, serialNumber.orNull,
        validFrom.map(d => d: Date).orNull, expires.map(d => d: Date).orNull,
        enabled.getOrElse(false), cancelled.getOrElse(false), onHotList.getOrElse(false),
        technology.orNull, networks.orNull, allows.orNull,
        accountKey.getOrElse(0L), replacementDate.map(d => d: Date), replacementReason,
        collected.map(d => d: Date), posted.map(d => d: Date), customerId.orNull,
        cvv.orNull, brand.orNull, cardKey)
  }

  private def query(condition: Fragment): List[MappedPhysicalCard] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[MappedPhysicalCard] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAll(): List[MappedPhysicalCard] = query(fr"ORDER BY id ASC")

  def findByBankAndCardId(bankId: String, cardId: String): Box[MappedPhysicalCard] =
    one(fr"WHERE mbankid = $bankId AND mcardid = $cardId")

  def findByCardNumber(bankCardNumber: String): Box[MappedPhysicalCard] =
    one(fr"WHERE mbankcardnumber = $bankCardNumber")

  def findByBankSerialAndCardNumber(bankId: String, serialNumber: String,
                                    bankCardNumber: String): Box[MappedPhysicalCard] =
    one(fr"""WHERE mbankid = $bankId AND mserialnumber = $serialNumber
             AND mbankcardnumber = $bankCardNumber""")

  def findAllForBank(bankId: String, customerId: Option[String],
                     accountKey: Option[Long]): List[MappedPhysicalCard] = {
    val conditions = List(
      Some(fr"mbankid = $bankId"),
      customerId.map(v => fr"mcustomerid = $v"),
      accountKey.map(v => fr"maccount = $v")
    ).flatten
    query(fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b) ++ fr"ORDER BY id ASC")
  }

  def insert(cardId: String, bankId: String, bankCardNumber: String, cardType: String,
             nameOnCard: String, issueNumber: String, serialNumber: String, validFrom: Date,
             expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean,
             technology: String, networks: String, allows: String, accountKey: Long,
             replacementDate: Option[Date], replacementReason: Option[String],
             collected: Option[Date], posted: Option[Date], customerId: String, cvv: String,
             brand: String): MappedPhysicalCard = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedphysicalcard
            (mcardid, mbankid, mbankcardnumber, mcardtype, mnameoncard, missuenumber,
             mserialnumber, mvalidfrom, mexpires, menabled, mcancelled, monhotlist, mtechnology,
             mnetworks, mallows, maccount, mreplacementdate, mreplacementreason, mcollected,
             mposted, mcustomerid, mcvv, mbrand)
            VALUES ($cardId, $bankId, $bankCardNumber, $cardType, $nameOnCard, $issueNumber,
             $serialNumber, ${new java.sql.Timestamp(validFrom.getTime)},
             ${new java.sql.Timestamp(expires.getTime)}, $enabled, $cancelled, $onHotList,
             $technology, $networks, $allows, $accountKey,
             ${replacementDate.map(d => new java.sql.Timestamp(d.getTime))}, $replacementReason,
             ${collected.map(d => new java.sql.Timestamp(d.getTime))},
             ${posted.map(d => new java.sql.Timestamp(d.getTime))}, $customerId, $cvv, $brand)"""
        .update.run)
    findByBankAndCardId(bankId, cardId)
      .openOrThrowException("the physical card just inserted must be readable")
  }

  /**
   * cvv and brand are NOT written here. Mapper's update path did not set them either, so an update
   * leaves the values the create wrote — including the hashed CVV, which an update must not
   * re-hash from a plaintext it was never given.
   */
  def update(cardKey: Long, cardId: String, bankId: String, bankCardNumber: String,
             cardType: String, nameOnCard: String, issueNumber: String, serialNumber: String,
             validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean,
             onHotList: Boolean, technology: String, networks: String, allows: String,
             accountKey: Long, replacementDate: Option[Date], replacementReason: Option[String],
             collected: Option[Date], posted: Option[Date], customerId: String): Box[MappedPhysicalCard] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedphysicalcard SET mcardid = $cardId, mbankid = $bankId,
              mbankcardnumber = $bankCardNumber, mcardtype = $cardType, mnameoncard = $nameOnCard,
              missuenumber = $issueNumber, mserialnumber = $serialNumber,
              mvalidfrom = ${new java.sql.Timestamp(validFrom.getTime)},
              mexpires = ${new java.sql.Timestamp(expires.getTime)}, menabled = $enabled,
              mcancelled = $cancelled, monhotlist = $onHotList, mtechnology = $technology,
              mnetworks = $networks, mallows = $allows, maccount = $accountKey,
              mreplacementdate = ${replacementDate.map(d => new java.sql.Timestamp(d.getTime))},
              mreplacementreason = $replacementReason,
              mcollected = ${collected.map(d => new java.sql.Timestamp(d.getTime))},
              mposted = ${posted.map(d => new java.sql.Timestamp(d.getTime))},
              mcustomerid = $customerId
            WHERE id = $cardKey""".update.run)
    one(fr"WHERE id = $cardKey")
  }

  def delete(bankId: String, cardId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedphysicalcard WHERE mbankid = $bankId AND mcardid = $cardId"
        .update.run) > 0

  def deleteByAccountKey(accountKey: Long): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedphysicalcard WHERE maccount = $accountKey".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedphysicalcard".update.run)
    ()
  }
}

object PinReset {

  /** Ordered by id ascending, as the Lift OneToMany was. */
  def findAllByCardKey(cardKey: Long): List[PinResetInfo] =
    DoobieUtil.runQuery(
      sql"""SELECT mreplacementdate, mreplacementreason FROM pinreset
            WHERE card = $cardKey ORDER BY id ASC"""
        .query[(java.sql.Timestamp, String)].to[List])
      .map { case (date, reason) => PinResetInfo(date, PinResetReason.valueOf(reason)) }

  /**
   * Mapper looked an existing reset up by mReplacementDate ALONE, ignoring which card it belonged
   * to, so a reset requested on the same instant for a different card was updated instead of a new
   * row being inserted. Preserved verbatim — narrowing the lookup to the card would change which
   * rows exist.
   */
  def upsertByReplacementDate(cardKey: Long, requestedDate: Date, reason: String): Unit = {
    val ts = new java.sql.Timestamp(requestedDate.getTime)
    val updated = DoobieUtil.runUpdate(
      sql"UPDATE pinreset SET mreplacementreason = $reason WHERE mreplacementdate = $ts".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO pinreset (card, mreplacementdate, mreplacementreason)
              VALUES ($cardKey, $ts, $reason)"""
          .update.run)
    }
    ()
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM pinreset".update.run)
    ()
  }
}

object MappedPhysicalCardProvider extends PhysicalCardProvider {

  /** The numeric MAPPEDBANKACCOUNT key the card's foreign key column holds. */
  private def accountKeyOrThrow(bankId: String, accountId: String): Long =
    MappedBankAccount
      .find(bankId, accountId)
      .openOrThrowException(s"$accountId do not have Primary key, please contact admin, check the database! ")
      .accountPrimaryKey

  private def applyPinResets(card: MappedPhysicalCard, pinResets: List[PinResetInfo]): Unit =
    pinResets.foreach { pinReset =>
      PinReset.upsertByReplacementDate(card.cardKey, pinReset.requestedDate,
        pinReset.reasonRequested.toString)
    }

  override def updatePhysicalCard(
    cardId: String,
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
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
    posted: Option[CardPostedInfo],
    customerId: String,
    callContext: Option[CallContext]
  ): Box[MappedPhysicalCard] = {
    val accountKey = accountKeyOrThrow(bankId, accountId)

    // Mapper wrote CardReplacementInfo(null, null).reasonRequested.toString for an absent
    // replacement, i.e. the literal "null" rather than SQL NULL. Preserved: the replacement
    // accessor reads a present reason back and CardReplacementReason.valueOf would be handed the
    // same string either way.
    val (requestedDate, reasonRequested) = replacement match {
      case Some(c) => (Option(c.requestedDate), Some(String.valueOf(c.reasonRequested)))
      case _ => (None, Some(String.valueOf(null)))
    }

    val result = MappedPhysicalCard.findByBankAndCardId(bankId, cardId) match {
      case Full(existing) =>
        tryo {
          MappedPhysicalCard.update(existing.cardKey, cardId, bankId, bankCardNumber, cardType,
            nameOnCard, issueNumber, serialNumber, validFrom, expires, enabled, cancelled,
            onHotList, technology, networks.mkString(","), allows.mkString(","), accountKey,
            requestedDate, reasonRequested, collected.map(_.date), posted.map(_.date), customerId)
        }.flatMap(box => box) ?~! ErrorMessages.UpdateCardError
      case _ =>
        Failure(s"${ErrorMessages.CardNotFound} Current BankId($bankId) and CardId($cardId) ")
    }
    result match {
      case Full(v) => applyPinResets(v, pinResets)
      case _ => // There is no enough information to set foreign key
    }
    result
  }

  override def createPhysicalCard(
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
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
    posted: Option[CardPostedInfo],
    customerId: String,
    cvv: String,
    brand: String,
    callContext: Option[CallContext]
  ): Box[MappedPhysicalCard] = {
    val accountKey = accountKeyOrThrow(bankId, accountId)

    // Unlike the update path, create left the replacement columns genuinely NULL when absent.
    val (requestedDate, reasonRequested) = replacement match {
      case Some(c) => (Option(c.requestedDate), Option(c.reasonRequested).map(_.toString))
      case _ => (None, None)
    }

    val result = MappedPhysicalCard.findByBankSerialAndCardNumber(bankId, serialNumber, bankCardNumber) match {
      case Full(_) =>
        Failure(s"${ErrorMessages.CardAlreadyExists} Current BankId($bankId), bankCardNumber($bankCardNumber) and serialNumber($serialNumber)")
      case _ =>
        tryo {
          MappedPhysicalCard.insert(APIUtil.generateUUID(), bankId, bankCardNumber, cardType,
            nameOnCard, issueNumber, serialNumber, validFrom, expires, enabled, cancelled,
            onHotList, technology, networks.mkString(","), allows.mkString(","), accountKey,
            requestedDate, reasonRequested, collected.map(_.date), posted.map(_.date), customerId,
            HashUtil.Sha256Hash(cvv), brand)
        } ?~! ErrorMessages.CreateCardError
    }
    result match {
      case Full(v) => applyPinResets(v, pinResets)
      case _ => // There is no enough information to set foreign key
    }
    result
  }

  def getPhysicalCards(user: User): List[MappedPhysicalCard] = {
    val accounts = views.vend.getPrivateBankAccounts(user)
    val allCards = MappedPhysicalCard.findAll()
    for {
      account <- accounts
      card <- allCards if account.accountId.value == card.account.accountId.value
    } yield card
  }

  override def getPhysicalCardByCardNumber(bankCardNumber: String,
                                           callContext: Option[CallContext]): Box[PhysicalCardTrait] =
    MappedPhysicalCard.findByCardNumber(bankCardNumber)

  def getPhysicalCardsForBank(bank: Bank, user: User,
                              queryParams: List[OBPQueryParam]): List[MappedPhysicalCard] = {
    val customerId = queryParams.collectFirst { case OBPCustomerId(value) => value }
    val accountKey = queryParams.collectFirst { case OBPAccountId(value) =>
      // An account id that does not resolve becomes Long.MaxValue, which matches no card — the
      // same "no results" Mapper produced rather than an error.
      MappedBankAccount
        .find(bank.bankId.value, value)
        .map(_.accountPrimaryKey).openOr(Long.MaxValue)
    }
    MappedPhysicalCard.findAllForBank(bank.bankId.value, customerId, accountKey)
  }

  def getPhysicalCardsForUser(bank: Bank, user: User): List[MappedPhysicalCard] = {
    val allCards = MappedPhysicalCard.findAll()
    for {
      account <- views.vend.getPrivateBankAccounts(user, bank.bankId)
      card <- allCards if account.accountId.value == card.account.accountId.value
    } yield card
  }

  override def getPhysicalCardForBank(bankId: BankId, cardId: String,
                                      callContext: Option[CallContext]): Box[MappedPhysicalCard] =
    MappedPhysicalCard.findByBankAndCardId(bankId.value, cardId)

  override def deletePhysicalCardForBank(bankId: BankId, cardId: String,
                                         callContext: Option[CallContext]): Box[Boolean] =
    MappedPhysicalCard.findByBankAndCardId(bankId.value, cardId)
      .map(_ => MappedPhysicalCard.delete(bankId.value, cardId))
}

// The Lift entity `CardAction` used to be declared here. It was never registered for schema
// creation, so its table has never existed in any database and no code path read or wrote it.
// Dropped rather than migrated.
