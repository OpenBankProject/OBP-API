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
import net.liftweb.util.Helpers.tryo

/**
 * This object answers the two questions OBP asks about a currency code on almost every request
 * that carries an amount: is the code known, and how many decimal places does it have. It answers
 * them from the asset registry ([[Assets]]), so that `APIUtil.isValidCurrencyISOCode` and
 * `Helper.currencyDecimalPlaces` no longer depend on a list built into the code.
 *
 * The answers are the same as before the registry existed (ideas/ASSET_REGISTRY.md, progress step 6):
 *
 * - Codes are matched exactly as written, so `EUR` is known and `eur` is not. Accepting any letter
 *   case is a later, deliberate change (section 4).
 * - The status of an asset is not checked yet: a suspended or retired code is still known. That
 *   also comes later, when `isValidCurrencyISOCode` becomes `isUsableAsset` (section 4).
 * - Three spellings the built-in list accepts are not registry codes: `ada` (the registry holds
 *   `ADA`), and `lovelace` and `wei`, the smallest units of ADA and ETH. They stay known, with the
 *   built-in decimal places, until amounts in those units are converted (section 7, Part C).
 * - A code the registry does not hold gets the built-in decimal places, as it did before.
 *
 * The registry is read once into memory and read again after any write through [[Assets]]. If it
 * cannot be read (no database, as in pure unit tests) or is still empty (before the boot seed has
 * run), the built-in answers are used and nothing is kept, so the next call tries the registry again.
 * Until the precisions are corrected (section 7, Part B) the built-in answers and the registry's
 * agree for every code, because the seed copies them.
 */
object AssetLookup extends MdcLoggable {

  /** The spellings the built-in list accepts that are not codes in the registry. */
  val LegacySpellings: Set[String] = Set("ada", "lovelace", "wei")

  /** The decimal places of every registered asset, keyed by its code exactly as stored. */
  @volatile private var decimalPlacesByCode: Option[Map[String, Int]] = None

  /** Forgets what was read, so the next lookup reads the registry again. Called after every write. */
  def invalidate(): Unit = decimalPlacesByCode = None

  private def registry: Option[Map[String, Int]] = decimalPlacesByCode.orElse {
    tryo(Assets.getAssets().map(asset => asset.assetCode -> asset.decimalPlaces).toMap) match {
      case Full(loaded) if loaded.nonEmpty =>
        decimalPlacesByCode = Some(loaded)
        decimalPlacesByCode
      case Full(_) =>
        logger.debug("registry says: the asset registry is empty; using the built-in currency list")
        None
      case failure =>
        logger.debug(s"registry says: could not read the asset registry, using the built-in currency list: $failure")
        None
    }
  }

  /** Whether OBP knows this currency code, matched exactly as written. */
  def isKnownCode(code: String): Boolean = registry match {
    case Some(codes) => codes.contains(code) || LegacySpellings.contains(code)
    case None => APIUtil.builtInCurrencyCodes.contains(code)
  }

  /** The number of decimal places of this currency code, matched exactly as written. */
  def decimalPlaces(code: String): Int =
    registry.flatMap(_.get(code)).getOrElse(Helper.builtInCurrencyDecimalPlaces(code))
}
