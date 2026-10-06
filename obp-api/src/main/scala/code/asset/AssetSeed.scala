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

package code.asset

import code.api.util.APIUtil
import code.util.Helper
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

/**
 * This object fills the asset registry with the assets every OBP instance knows: the currencies,
 * precious metals and accounting units in media/xml/ISOCurrencyCodes.xml, plus the crypto assets
 * XBT, ADA and ETH. It runs at every boot (Boot.scala) and only inserts codes that are missing; it
 * never changes an existing row, so a deployment's own changes (a suspended code, a corrected
 * name) survive a restart. Several instances booting at once are safe: the unique index on the code
 * lets only one insert win, and the others count the code as already present.
 *
 * Every seeded asset gets the number of decimal places OBP uses for its code today
 * (`Helper.builtInCurrencyDecimalPlaces`), not the ISO value, so that switching the lookups over to the
 * registry (AssetLookup) changes nothing. Correcting precisions is a separate, later step
 * (ideas/ASSET_REGISTRY.md, section 7, Part B).
 *
 * There is deliberately no property to switch the seed off: a deployment that does not handle a
 * code suspends it in the registry, so there is one model and one audit trail.
 */
object AssetSeed extends MdcLoggable {

  /** The value stored in `created_by_user_id` for seeded rows; there is no real User behind them. */
  val SeedActor = "system:asset-seed"

  case class Entry(assetCode: String, assetType: String, name: String, decimalPlaces: Int)

  private val PreciousMetalCodes = Set("XAU", "XAG", "XPT", "XPD")

  /**
   * The crypto entries in the XML file. They are not taken from the file: `ada` is seeded as `ADA`
   * with the crypto assets below, and `lovelace` and `wei` are the smallest units of ADA and ETH,
   * not assets of their own, so they are not seeded at all.
   */
  private val CryptoCodesInXmlFile = Set("ADA", "LOVELACE", "ETH", "WEI")

  val cryptoEntries: List[Entry] = List(
    Entry("XBT", AssetTypes.CRYPTO, "Bitcoin", Helper.builtInCurrencyDecimalPlaces("XBT")),
    Entry("ADA", AssetTypes.CRYPTO, "Cardano ADA", Helper.builtInCurrencyDecimalPlaces("ADA")),
    Entry("ETH", AssetTypes.CRYPTO, "Ether", Helper.builtInCurrencyDecimalPlaces("ETH"))
  )

  /**
   * One entry per distinct code in the XML file, other than the crypto codes. A code that appears
   * for several countries (EUR, USD) takes the currency name from its first entry. An ISO minor unit
   * of "N.A." marks an accounting unit (XDR, XTS, ...), except for the four precious metals.
   */
  lazy val isoEntries: List[Entry] = {
    val rows = (APIUtil.CurrencyIsoCodeFromXmlFile \ "CcyTbl" \ "CcyNtry").toList
      .map(row => ((row \ "Ccy").text.trim, (row \ "CcyNm").text.trim, (row \ "CcyMnrUnts").text.trim))
      .filter { case (code, _, _) => code.nonEmpty && !CryptoCodesInXmlFile.contains(code.toUpperCase) }
    rows.groupBy(_._1).toList.map { case (code, rowsForCode) =>
      val (_, name, minorUnits) = rowsForCode.head
      val assetType =
        if (PreciousMetalCodes.contains(code)) AssetTypes.PRECIOUS_METAL
        else if (minorUnits == "N.A.") AssetTypes.ACCOUNTING_UNIT
        else AssetTypes.FIAT
      Entry(code, assetType, name, Helper.builtInCurrencyDecimalPlaces(code))
    }.sortBy(_.assetCode)
  }

  def entries: List[Entry] = isoEntries ++ cryptoEntries

  /** Inserts every missing entry and returns how many were inserted, already present, and failed. */
  def run(): (Int, Int, Int) = {
    val existingCodes = Assets.getAssets().map(_.assetCode).toSet
    val (inserted, alreadyPresent, failed) = entries.foldLeft((0, 0, 0)) {
      case ((inserted, alreadyPresent, failed), entry) =>
        if (existingCodes.contains(entry.assetCode)) (inserted, alreadyPresent + 1, failed)
        else Assets.createAsset(entry.assetCode, entry.assetType, entry.name, entry.decimalPlaces,
          issuerBankId = None, status = AssetStatuses.ACTIVE, createdByUserId = SeedActor) match {
          case Full(_) => (inserted + 1, alreadyPresent, failed)
          // Another instance booting at the same time may have inserted it first.
          case _ if Assets.getAsset(entry.assetCode).isDefined => (inserted, alreadyPresent + 1, failed)
          case failure =>
            logger.warn(s"run says: could not seed asset ${entry.assetCode}: $failure")
            (inserted, alreadyPresent, failed + 1)
        }
    }
    logger.info(s"run says: inserted=$inserted alreadyPresent=$alreadyPresent failed=$failed (of ${entries.size} seed entries)")
    (inserted, alreadyPresent, failed)
  }
}
