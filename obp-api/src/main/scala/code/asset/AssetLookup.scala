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
 * Codes are matched ignoring letter case, so `eur` is EUR and gets EUR's decimal places
 * ([[CurrencyCodes]]). Otherwise the answers are the ones OBP gave before the registry existed:
 *
 * - The status of an asset is not checked yet: a suspended or retired code is still known. That
 *   comes later, when `isValidCurrencyISOCode` becomes `isUsableAsset` (section 4).
 * - `lovelace` and `wei`, the smallest units of ADA and ETH, are not registry codes but stay known,
 *   with the built-in decimal places, until amounts in those units are converted (section 7, Part C).
 * - A code the registry does not hold gets the built-in decimal places, as it did before.
 *
 * The registry is read once into memory and read again after any write through [[Assets]]. If it
 * cannot be read (no database, as in pure unit tests) or is still empty (before the boot seed has
 * run), the built-in answers are used and nothing is kept, so the next call tries the registry again.
 * Until the precisions are corrected (section 7, Part B) the built-in answers and the registry's
 * agree for every code, because the seed copies them.
 */
object AssetLookup extends MdcLoggable {

  /** The codes the built-in list accepts that the registry does not hold, upper case. */
  val LegacySpellings: Set[String] = Set("LOVELACE", "WEI")

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

  private lazy val builtInCodesUpperCase: Set[String] = APIUtil.builtInCurrencyCodes.map(CurrencyCodes.normalise)

  /** Whether OBP knows this currency code, ignoring letter case. */
  def isKnownCode(code: String): Boolean = code != null && {
    val normalised = CurrencyCodes.normalise(code)
    registry match {
      case Some(codes) => codes.contains(normalised) || LegacySpellings.contains(normalised)
      case None => builtInCodesUpperCase.contains(normalised)
    }
  }

  /** The number of decimal places of this currency code, ignoring letter case. */
  def decimalPlaces(code: String): Int = {
    val normalised = CurrencyCodes.normalise(code)
    registry.flatMap(_.get(normalised)).getOrElse(Helper.builtInCurrencyDecimalPlaces(normalised))
  }
}
