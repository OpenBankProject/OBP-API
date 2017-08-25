package code.cards

import java.util.Date

import code.model._
import net.liftweb.util.SimpleInjector
import net.liftweb.common.{Box}

object PhysicalCard extends SimpleInjector {

  val physicalCardProvider = new Inject(buildOne _) {}

  def buildOne: PhysicalCardProvider = MappedPhysicalCardProvider

}

trait PhysicalCardProvider {

  def createOrUpdatePhysicalCard(bankCardNumber: String,
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
                     ): Box[MappedPhysicalCard]

  def getPhysicalCards(user: User): List[MappedPhysicalCard]

  def getPhysicalCardsForBank(bank: Bank, user: User): List[MappedPhysicalCard]

}








