/*
 * Copyright (c) 2009 Thomas Knierim
 * http://www.thomasknierim.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package currency

import java.math.{BigDecimal => BigDec}

/** Represents an immutable, arbitrary-precision currency value with fixed point
  * arithmetic. <p>A <code>Currency</code> value consists of a numeric value,
  * an optional currency designation, and an optional number of decimal places.
  * The default number of positions after the decimal point is 2 or respectively
  * the number of decimal places commonly used for the specified currency.</p>
  *
  * <p>Its numeric value is internally represented by a 128-bit <code>java.math.BigDecimal</code>
  * with 34 significant decimal places. This allows high-precision computations over
  * a large range of values. As an added benefit of using <code>java.math.BigDecimal</code>,
  * the <code>Currency</code> type gives users complete control over rounding behaviour.
  * The default rounding more is <code>ROUND_HALF_UP</code> which is commonly used in
  * commercial applications. The <code>ROUND_HALF_EVEN</code> mode may be useful for statistical 
  * applications.<p>
  *
  * <p>Computations are carried out with a precision of <i>n</i> positions after the decimal
  * point, whereas <i>n</i> is specified by the <code>decimals</code> parameter. The position
  * at <i>n + 1</i> is rounded according to the specified rounding mode.</p>
  *
  * <p>The <code>Currency</code> class distinguishes between specific and non-specific
  * currencies. Specific currencies carry a designation that is specified by the respective
  * ISO 4217 three-letter currency code. Non-specific values are values without currency code.
  * Non-specific values can be added to or subtracted from specific values. Used with
  * non-specific values only, the <code>Currency</code> class offers general purpose
  * fixed-point arithmetic.</p>
  *
  * @author Thomas Knierim
  * @version 1.0
  *
  * 20090506 tk  v  first release
  * 20100312 tk  f  fixed errors in country code map
  *
  */
@serializable
class Currency (value: BigDec, val currencyCode: String, val decimals: Int,
  val roundingMode: Currency.RoundingMode.Value) extends Ordered[Currency]{

  if (decimals < 0)
    throw new IllegalArgumentException("Currency decimal places must not be negative.")

/** Contains the amount of the <code>Currency</code> value as
  * <code>java.math.BigDecimal</code>.
  */
  val amount = value.setScale(decimals, roundingMode.id)

/** Returns a new <code>Currency</code> value with the specified number of decimals
  * after the decimal point whose value is equal to <code>this</code> value. If the
  * number of decimals is decreased, loss of precision may incur and the last position
  * is rounded according to the specified round mode (<code>ROUND_HALF_UP</code> by
  * default).
  *
  * @param d number of decimal places in the result value.
  * @return this <code>Currency</code> value with <i>d</i> decimal places.
  */
  def setDecimals(d: Int) = new Currency(amount, currencyCode, d, roundingMode)

/** Compares this <code>Currency</code> value with the specified value for equality.
  * Two currency values are considered equal if they have the same currency code
  * and the same numeric value. Decimals and rounding mode are not regarded.
  * Note that this means that equal currency values can have different string
  * representations, e.g. <code>"10 USD" == "10.00 USD"</code>.
  *
  * @param the <code>Currency</code> value to comapare to <code>this</code> value.
  * @return <code>true</code> if the values are equal, <code>false</code> otherwise.
  */
  override def equals(that: Any): Boolean = that match {
    case that: Currency => this.currencyCode == that.currencyCode &&
      this.amount.compareTo(that.amount) == 0
    case _ => false
  }

/* Trims trailing zeros from decimal positions of a numeric String
 */
  private def trimZeros(s: String) = {
    if (s.indexOf('.') > -1) {
      var i = s.length
      while(i > 0 && s(i - 1) == '0') i -= 1
      if (s(i - 1) == '.') i -= 1
      s.substring(0, i)
    }
    else s
  }

/** Returns a hash code for this <code>Currency</code> value.
  *
  * @return hash code for <code>this</code> value.
  */
  override def hashCode: Int =
    41 * (41 + trimZeros(amount.toString).hashCode) + currencyCode.hashCode

/** Compares this <code>Currency</code> value with the specified value.
  * Throws MismatchedCurrencyException if two values with
  * different currency codes are compared.
  *
  * @param that <code>Currency</code> value to which <code>this</code> value is compared.
  * @return -1, 0, or 1 as this <code>Currency</code> value is less than, equal to, or
  * greater than <code>that</code>.
  * @throws MismatchedCurrencyException if currency codes don't match.
  */
  def compare(that: Currency): Int =
    if (this.currencyCode != that.currencyCode)
      throw new MismatchedCurrencyException
    else
      this.amount.compareTo(that.amount)

/** Returns the smaller of this <code>Currency</code> value and the given value.
  * If both values are equal, <code>this</code> is returned.
  *
  * @param that <code>Currency</code> value to compare to.
  * @return the smaller <code>Currency</code> value.
  * @throws MismatchedCurrencyException if currency codes don't match.
  */
  def min (that: Currency): Currency =
    if (this.compare(that) <= 0) this else that

/** Returns the larger of this <code>Currency</code> value and the given value.
  * If both values are equal, <code>this</code> is returned.
  *
  * @param that <code>Currency</code> value to compare to.
  * @return the larger <code>Currency</code> value.
  * @throws MismatchedCurrencyException if currency codes don't match.
  */
  def max (that: Currency): Currency =
    if (this.compare(that) >= 0) this else that

/** Adds a <code>Currency</code> value to this value and returns the sum as a new
  * <code>Currency</code> value. The number of decimal places in the result is
  * automatically adjusted to that of the summand with the wider scale. Currency
  * codes need to be regarded:
  * <ul>
  * <li><code>55.55 EUR + 33.33 EUR = 88.88 EUR></code></li>
  * <li><code>55.55 EUR + 33.33 = 88.88 EUR></code></li>
  * <li><code>55.55 EUR + 33.33 USD = MismatchedCurrencyException</code></li>
  * <li><code>55.5555 EUR + 33.33 EUR = 88.8855 EUR</code></li>
  * </ul>
  *
  * @param that <code>Currency</code> value to add.
  * @return the sum of both <code>Currency</code> values.
  * @throws MismatchedCurrencyException if currency codes don't match.
  *
  */
  def +  (that: Currency): Currency = {
    var code = this.currencyCode
    if (code.isEmpty)
      code = that.currencyCode
    else if (this.currencyCode != that.currencyCode && !that.currencyCode.isEmpty)
      throw new MismatchedCurrencyException
    val result = this.amount.add(that.amount)
    new Currency(result, code, result.scale, this.roundingMode)
  }

/** Subtracts a <code>Currency</code> value from this value and returns the difference
  * as a new <code>Currency</code> value. The number of decimal places in the result is
  * automatically adjusted to that of the argument with the wider scale. Currency
  * codes need to be regarded:
  * <ul>
  * <li><code>55.55 EUR - 33.33 EUR = <b>22.22 EUR</b></code></li>
  * <li><code>55.55 EUR - 33.33 = <b>22.22 EUR</b></code></li>
  * <li><code>55.55 EUR - 33.33 USD = <b>MismatchedCurrencyException</b></code></li>
  * <li><code>55.5555 EUR - 33.33 EUR = <b>22.2255 EUR</b></code></li>
  * </ul>
  *
  * @param that <code>Currency</code> value to subtract.
  * @return the value of <code>this - that</code>.
  * @throws MismatchedCurrencyException if currency codes don't match.
  */
   def - (that: Currency): Currency = {
    var code = this.currencyCode
    if (code.isEmpty)
      code = that.currencyCode
    else if (this.currencyCode != that.currencyCode && !that.currencyCode.isEmpty)
      throw new MismatchedCurrencyException
    val result = this.amount.subtract(that.amount)
    new Currency(result, code, result.scale, this.roundingMode)
  }

/** Multiplies this <code>Currency</code> value with a <code>java.math.BigDecimal</code>
  * scalar value and returns a new <code>Currency</code> value.
  *
  * @param that the mulitiplier.
  * @return the value of <code>this * that</code>.
  */
  def * (that: BigDec): Currency =
    new Currency(this.amount.multiply(that), currencyCode, decimals, roundingMode)

/** Multiplies this <code>Currency</code> value with a <code>Double</code>
  * scalar value and returns a new <code>Currency</code> value.
  *
  * @param that the mulitiplier.
  * @return the value of <code>this * that</code>.
  */
  def * (that: Double): Currency = this * BigDec.valueOf(that)

/** Multiplies this <code>Currency</code> value with a <code>Long</code>
  * scalar value and returns a new <code>Currency</code> value.
  *
  * @param that the mulitiplier.
  * @return the value of <code>this * that</code>.
  */
  def * (that: Long): Currency = this * BigDec.valueOf(that)

/** Divides this <code>Currency</code> value by a <code>java.math.BigDecimal</code>
  * scalar value and returns a new <code>Currency</code> value.
  *
  * @param that the divisor.
  * @return the value of <code>this / that</code>.
  */
  def / (that: BigDec): Currency =
    new Currency(this.amount.divide(that, roundingMode.id), currencyCode, decimals, roundingMode)

/** Divides this <code>Currency</code> value by a <code>Double</code>
  * scalar value and returns a new <code>Currency</code> value.
  *
  * @param that the divisor.
  * @return the value of <code>this / that</code>.
  */
  def / (that: Double): Currency = this / BigDec.valueOf(that)

/** Divides this <code>Currency</code> value by a <code>Long</code>
  * scalar value and returns a new <code>Currency</code> value.
  *
  * @param that the divisor.
  * @return the value of <code>this / that</code>.
  */
  def / (that: Long): Currency = this / BigDec.valueOf(that)

  /** Returns a <code>Currency</code> whose value is <code>-this</code>.
    *
    * @return -this.
    */
  def unary_- : Currency = new Currency(amount.negate(), currencyCode, decimals, roundingMode)

  /** Returns a <code>Currency</code> whose value is the absolute value of this
    * <code>Currency</code>.
    *
    * @return abs(this).
    *
    */
  def abs: Currency = new Currency(amount.abs(), currencyCode, decimals, roundingMode)

/** Returns a <code>Currency</code> whose value is <code>this * p / 100</code>.
  *
  * @param p the percentage.
  * @return the result of the applied percentage.
  */
  def percent(p: Double) = (this * p) / 100

/** Returns a <code>Currency</code> whose value is the integer part of this value.
  * Example: <code>Currency(2.50).integral</code> returns <code>Currency(2.00)</code>.
  *
  * @return the integral part of this value.
  */
  def integral = new Currency(amount.divideToIntegralValue(new BigDec("1")),
    currencyCode, decimals, roundingMode)

/** Returns a <code>Currency</code> whose value is the fractional part of this value.
  * Example: <code>Currency(2.50).fraction</code> returns <code>Currency(0.50)</code>.
  *
  * @return the fractionalal part of this value.
  */
  def fraction = new Currency(amount.subtract(integral.amount),
      currencyCode, decimals, roundingMode)

/** Returns a <code>Currency</code> whose value is <code>(this<sup>exp</sup>).
  *
  * @param exp the power to raise this <code>Currency</code> value to.
  * @return this<sup>exp</sup>.
  */
  def pow(exp: Int) = new Currency(amount.pow(exp), currencyCode, decimals, roundingMode)

/** Returns a canonical <code>String</code> representation of the <code>Currency</code>
  * value with 3-letter ISO currency code.
  *
  * @return formatted String with <code>decimals</code> positions after decimal point.
  */
  override def toString = {
    amount.toString +
    (if (currencyCode.isEmpty) "" else " " + currencyCode)
   }

/** Returns the amount of the <code>Currency</code> value as <code>Double</code> value.
  * If this value has too great a magnitude represent as a <code>Double</code>, it will
  * be converted to <code>Double.NEGATIVE_INFINITY</code> or
  * <code>Double.POSITIVE_INFINITY</code> as appropriate.
  *
  * @return this <code>Currency</code> value converted to a <code>Double</code>.
  */
  def toDouble = amount.doubleValue

/** Returns the amount of the <code>Currency</code> value as <code>Float</code> value.
  * If this value has too great a magnitude represent as a <code>Float</code>, it will
  * be converted to <code>Float.NEGATIVE_INFINITY</code> or
  * <code>Float.POSITIVE_INFINITY</code> as appropriate.
  *
  * @return this <code>Currency</code> value converted to a <code>Float</code>.
  */
  def toFloat = amount.floatValue

/** Returns the amount of the <code>Currency</code> value as <code>Long</code> value.
  * Any fractional part will be discarded. If this value is too big to fit in a
  * <code>Long</code>, only the low-order 64 bits are returned.
  *
  * @return this <code>Currency</code> value converted to a <code>Long</code>.
  */
  def toLong = amount.longValue

/** Returns the amount of the <code>Currency</code> value as an <code>Int</code> value.
  * Any fractional part will be discarded. If this value is too big to fit in an
  * <code>Int</code>, only the low-order 32 bits are returned.
  *
  * @return this <code>Currency</code> value converted to an <code>Int</code>.
  */
  def toInt = amount.intValue

/** Returns the currency symbol for this <code>Currency</code>, or the empty
  * string if this is a non-specific <code>Currency</code> value.
  *
  * @return Unicode currency symbol.
  *
  */
  def symbol = {
    if (currencyCode.isEmpty)
      ""
    else {
      var symbol = Currency.getSymbolFor(this.currencyCode)
      if (symbol.isEmpty)
        currencyCode
      else
        symbol
    }
  }

/** Returns the English name for this <code>Currency</code>, or the empty
  * string if this is a non-specific <code>Currency</code> value.
  *
  * @return currency name.
  */
  def name = Currency.getNameFor(this.currencyCode)

/** Formats a currency value according to default locale.
  * See <code>java.util.Locale</code> and <code>java.text.NumberFormat</code> for details.
  */
  def format: String = {
    val nf = java.text.NumberFormat.getCurrencyInstance()
    nf.setCurrency(java.util.Currency.getInstance("USD"))
    nf.format(amount).replace("USD", symbol).replace("$", symbol)
  }

/** Formats a currency value according to given locale.
  * See <code>java.util.Locale</code> and <code>java.text.NumberFormat</code> for details.
  */
  def format(locale: java.util.Locale): String = {
    val nf = java.text.NumberFormat.getCurrencyInstance(locale)
    nf.setCurrency(java.util.Currency.getInstance("USD"))
    nf.format(amount).replace("USD", symbol).replace("$", symbol)
  }

/** Formats a currency value according to the given pattern.
  * See <code>java.text.DecimalFormat</code> for details on format patterns.
  *
  * @param pattern a decimal number pattern string.
  * @throws IllegalArgumentException if the given pattern is invalid.
  */
  def format(pattern: String): String = {
    new java.text.DecimalFormat(pattern.
      replace("\u00A4\u00A4", currencyCode).
      replace("\u00A4", symbol)).
      format(amount)
  }

/** Formats a currency value according to the given pattern and with the
  * given decimal symbols. See <code>java.text.DecimalFormat</code> for
  * details on format patterns and symbols.
  *
  * @param pattern a decimal number pattern string.
  * @param symbols the set of symbols to be used.
  * @throws IllegalArgumentException if the given pattern is invalid.
  */
  def format(pattern: String, symbols: java.text.DecimalFormatSymbols): String = {
    new java.text.DecimalFormat(pattern.
      replace("\u00A4\u00A4", currencyCode).
      replace("\u00A4", symbol), symbols).
      format(amount)
  }

/** Formats a currency value according to the given <code>DecimalFormat</code> object.
  * This method gives you the most fine-grained control over the format output. It is
  * also the most efficient one for repetitive formatting of <code>Currency</code> values.
  * See <code>java.text.DecimalFormat</code> for details.
  *
  * @param format custom format object.
  * @throws IllegalArgumentException if the given custom format cannot be applied.
  */
  def format(format: java.text.DecimalFormat): String = format.format(amount)

/** Verbalises this <code>Currency</code> value in the English language. The amount is
  * spelled out with integral and fractional units and with currency name. Only numbers
  * that fit into a 64-bit <code>Long</code> integer can be verbalised. Larger numbers
  * are returned as numeric strings.
  *
  * @return this <code>Currency</code> value in words.
  */
  def say: String = say(java.util.Locale.ENGLISH)

/** Verbalises this <code>Currency</code> value in the given language. The amount is
  * spelled out with integral and fractional units and with currency name. Only numbers
  * that fit into a 64-bit <code>Long</code> integer can be verbalised. Larger numbers
  * are returned as numeric strings. Currently only English, French, Spanish and
  * German are supported.
  *
  * @return this <code>Currency</code> value in words.
  * @throws IllegalArgumentException if the language is not supported.
  */
  def say(locale: java.util.Locale): String =
  {
    val num = amount.divideAndRemainder(new BigDec("1"))
    val result = try {
      val integral = num(0).longValueExact()
      val fraction = num(1).movePointRight(decimals).abs.longValue()
      val language = locale.getLanguage()
      VerbaliseNumber.toWords(integral, language) + " " +
        Currency.getNameFor(currencyCode) +
        (if (language == "en" && Math.abs(integral) > 1) "s" else "") +
        (if (fraction != 0L)
           " " + VerbaliseNumber.toWords(fraction, locale.getLanguage()) + " " +
           Currency.getFractNameFor(currencyCode) +
           (if (language == "en" && fraction > 1) "s" else "")
        else "")
    } catch {
      case e: ArithmeticException => amount.toString + " " + Currency.getNameFor(currencyCode)
    }
    result
  }

/** Verbalises this <code>Currency</code> value in the English language. The integral
  * part of the amount is spelled out and the fractional part is written in the
  * form  <code>xx/100</code>. Only numbers that fit into a 64-bit <code>Long</code>
  * integer can be verbalised. Larger numbers are returned as numeric strings.
  * Currently only English, French, Spanish and German are supported.
  *
  * @return this <code>Currency</code> value in words.
  */
  def sayNumber: String = sayNumber(java.util.Locale.ENGLISH)

/** Verbalises this <code>Currency</code> value in the given language. The integral
  * part of the amount is spelled out and the fractional part is written in the
  * form  <code>xx/100</code>. Only numbers that fit into a 64-bit <code>Long</code>
  * integer can be verbalised. Larger numbers are returned as numeric strings.
  * Currently only English, French, Spanish and German are supported.
  *
  * @return this <code>Currency</code> value in words.
  * @throws IllegalArgumentException if the language is not supported.
  */
  def sayNumber(locale: java.util.Locale): String =
  {
    val num = amount.divideAndRemainder(new BigDec("1"))
    val result = try {
      val integral = num(0).longValueExact()
      val fraction = num(1).movePointRight(decimals).abs.longValue()
      VerbaliseNumber.toWords(integral, locale.getLanguage()) +
        (if (fraction != 0L)
           " " + fraction.toString + "/" + Math.pow(10,decimals).toLong.toString
        else "")
    } catch {
      case e: ArithmeticException => amount.toString + " " + Currency.getNameFor(currencyCode)
    }
    result
  }

}

/** This exception is thrown if an arithmetic operation is attempted on
  * two values with currencies that don't match.
  */
class MismatchedCurrencyException extends Exception

/** This exception is thrown if a currency with an unknown ISO ISO 4217 currency 
  * code is created or requested.
  */
class UnknownCurrencyException extends Exception

object Currency {

  @serializable
  object RoundingMode extends Enumeration {
    type RoundingMode = Value

/** Rounding mode to round away from zero. Always increments the digit prior to
  * a nonzero discarded fraction. Note that this rounding mode never decreases
  * the magnitude of the calculated value.
  */
    val ROUND_UP = Value;

/** Rounding mode to round towards zero. Never increments the digit prior to a
 *  discarded fraction (i.e., truncates). Note that this rounding mode never
 *  increases the magnitude of the calculated value.
 */
    val ROUND_DOWN = Value; 

/** Rounding mode to round towards positive infinity. If the <code>Currency</code>
  * value is positive, behaves as for ROUND_UP; if negative, behaves as for
  * ROUND_DOWN. Note that this rounding mode never decreases the calculated value.
  */
    val ROUND_CEILING = Value;

/** Rounding mode to round towards negative infinity. If the <code>Currency</code>
  * value is positive, behave as for ROUND_DOWN; if negative, behave as for
  * ROUND_UP. Note that this rounding mode never increases the calculated value.
  */
    val ROUND_FLOOR = Value;

/** Rounding mode to round towards "nearest neighbor" unless both neighbors
  * are equidistant, in which case round up. Behaves as for ROUND_UP if the
  * discarded fraction is >= 0.5; otherwise, behaves as for ROUND_DOWN. Note
  * that this is the rounding mode that most of us were taught in grade school.
  * <p>Also known as common rounding.</p>
  */
    val ROUND_HALF_UP = Value;

/** Rounding mode to round towards "nearest neighbor" unless both neighbors are
 *  equidistant, in which case round down. Behaves as for ROUND_UP if the discarded
 *  fraction is > 0.5; otherwise, behaves as for ROUND_DOWN.
 */
    val ROUND_HALF_DOWN = Value;

/** Rounding mode to round towards the "nearest neighbor" unless both neighbors
  * are equidistant, in which case, round towards the even neighbor. Behaves as
  * for ROUND_HALF_UP if the digit to the left of the discarded fraction is odd;
  * behaves as for ROUND_HALF_DOWN if it's even. Note that this is the rounding
  * mode that minimizes cumulative error when applied repeatedly over a sequence
  * of calculations. <p>Also known as Banker's Rounding.</p>
  */
    val ROUND_HALF_EVEN = Value;

 /** Rounding mode to assert that the requested operation has an exact result,
   * hence no rounding is necessary. If this rounding mode is specified on an
   * operation that yields an inexact result, an ArithmeticException is thrown.
   */
    val ROUND_UNNECESSARY = Value;
  }

  import Currency.RoundingMode._

/* ----- CURRENCY DATA ----- */

  private val currencies = Map(
    "AED" -> ("",     784, 2, "Fils",         "UAE Dirham", "AE"),
    "AFN" -> ("",     971, 2, "Pul",          "Afghani", "AF"),
    "ALL" -> ("L",      8, 2, "Qintar",       "Lek", "AL"),
    "AMD" -> ("դր",     51, 0, "Luma",         "Armenian Dram", "AM"),
    "ANG" -> ("ƒ",    532, 2, "Cent",         "Netherlands Antillean Guilder", "AN"),
    "AOA" -> ("Kz",   973, 1, "Cêntimo",      "Kwanza", "AO"),
    "ARS" -> ("$",     32, 2, "Centavo",      "Argentine Peso", "AR"),
    "AUD" -> ("$",     36, 2, "Cent",         "Australian Dollar", "AU"),
    "AWG" -> ("ƒ",    533, 2, "Cent",         "Aruban Florin", "AW"),
    "AZN" -> ("",     944, 2, "Qəpik",        "Azerbaijanian Manat", "AZ"),
    "BAM" -> ("КМ",   977, 2, "Fening",       "Convertible Marks", "BA"),
    "BBD" -> ("$",     52, 2, "Cent",         "Barbados Dollar", "BB"),
    "BDT" -> ("৳",     50, 2, "Paisa",        "Bangladeshi Taka", "BD"),
    "BGN" -> ("лв",   975, 2, "Stotinka",     "Bulgarian Lev", "BG"),
    "BHD" -> ("",      48, 3, "Fils",         "Bahraini Dinar", "BH"),
    "BIF" -> ("Fr",   108, 0, "Centime",      "Burundian Franc", "BI"),
    "BMD" -> ("$",     60, 2, "Cent",         "Bermuda Dollar", "BM"),
    "BND" -> ("$",     96, 2, "Sen",          "Brunei Dollar", "BN"),
    "BOB" -> ("Bs.",   68, 2, "Centavo",      "Boliviano", "BO"),
    "BRL" -> ("R$",   986, 2, "Centavo",      "Brazilian Real", "BR"),
    "BSD" -> ("$",     44, 2, "Cent",         "Bahamian Dollar", "BS"),
    "BTN" -> ("",      64, 2, "Chertrum",     "Ngultrum", "BT"),
    "BWP" -> ("P",     72, 2, "Thebe",        "Pula", "BW"),
    "BYR" -> ("Br",   974, 0, "Kapyeyka",     "Belarussian Ruble", "BY"),
    "BZD" -> ("$",     84, 2, "Cent",         "Belize Dollar", "BZ"),
    "CAD" -> ("$",    124, 2, "Cent",         "Canadian Dollar", "CA"),
    "CDF" -> ("Fr",   976, 2, "Centime",      "Franc Congolais", "CD"),
    "CHF" -> ("Fr",   756, 2, "Rappen",       "Swiss Franc", "CH"),
    "CLP" -> ("$",    152, 0, "Centavo",      "Chilean Peso", "CL"),
    "CNY" -> ("¥",    156, 1, "Fen",          "Yuan", "CN"),
    "COP" -> ("$",    170, 0, "Centavo",      "Colombian Peso", "CO"),
    "CRC" -> ("₡",    188, 2, "Céntimo",      "Costa Rican Colon", "CR"),
    "CUP" -> ("$",    192, 2, "Centavo",      "Cuban Peso", "CU"),
    "CVE" -> ("Esc",  132, 2, "Centavo",      "Cape Verde Escudo", "CV"),
    "CZK" -> ("Kč",   203, 2, "Haléř",        "Czech Koruna", "CZ"),
    "DJF" -> ("Fr",   262, 0, "Centime",      "Djibouti Franc", "DJ"),
    "DKK" -> ("kr",   208, 2, "Øre",          "Danish Krone", "DK"),
    "DOP" -> ("$",    214, 2, "Centavo",      "Dominican Peso", "DO"),
    "DZD" -> ("",      12, 2, "Centime",      "Algerian Dinar", "DZ"),
    "EEK" -> ("KR",   233, 2, "Sent",         "Kroon", "EE"),
    "EGP" -> ("£",    818, 2, "Piastre",      "Egyptian Pound", "EG"),
    "ERN" -> ("Nfk",  232, 2, "Cent",         "Nakfa", "ER"),
    "ETB" -> ("",     230, 2, "Santim",       "Ethiopian Birr", "ET"),
    "EUR" -> ("€",    978, 2, "Cent",         "Euro", "AT,BE,CY,ES,FI,FR,DE,GR,IE,IT,LU,MT,NL,PT,SI,SK"),
    "FJD" -> ("$",    242, 2, "Cent",         "Fiji Dollar", "FJ"),
    "FKP" -> ("£",    238, 2, "Penny",        "Falkland Islands Pound", "FK"),
    "GBP" -> ("£",    826, 2, "Penny",        "Pound Sterling", "UK"),
    "GEL" -> ("ლ",    981, 2, "Tetri",        "Lari", "GE"),
    "GHS" -> ("₵",    936, 2, "Pesewa",       "Cedi", "GH"),
    "GIP" -> ("£",    292, 2, "Penny",        "Gibraltar Pound", "GI"),
    "GMD" -> ("D",    270, 2, "Butut",        "Dalasi", "GM"),
    "GNF" -> ("Fr",   324, 0, "Centime",      "Guinea Franc", "GQ"),
    "GTQ" -> ("Q",    320, 2, "Centavo",      "Quetzal", "GT"),
    "GYD" -> ("$",    328, 2, "Cent",         "Guyana Dollar", "GY"),
    "HKD" -> ("$",    344, 1, "cent",         "Hong Kong Dollar", "HK"),
    "HNL" -> ("L",    340, 2, "Centavo",      "Lempira", "HN"),
    "HRK" -> ("kn",   191, 2, "Lipa",         "Croatian Kuna", "HR"),
    "HTG" -> ("G",    332, 2, "Centime",      "Haiti Gourde", "HT"),
    "HUF" -> ("Ft",   348, 0, "Penny",        "Fillér", "HU"),
    "IDR" -> ("Rp",   360, 0, "Sen",          "Rupiah", "ID"),
    "ILS" -> ("₪",    376, 2, "Agora",        "Israeli New Sheqel", "IL"),
    "INR" -> ("₨",    356, 2, "Penny",        "Paisa", "IN"),
    "IQD" -> ("",     368, 0, "Fils",         "Iraqi Dinar", "IQ"),
    "IRR" -> ("",     364, 0, "Dinar",        "Iranian Rial", "IR"),
    "ISK" -> ("kr",   352, 0, "Eyrir",        "Iceland Krona", "IS"),
    "JMD" -> ("$",    388, 2, "Cent",         "Jamaican Dollar", "JM"),
    "JOD" -> ("",     400, 3, "Piastre",      "Jordanian Dinar", "JO"),
    "JPY" -> ("¥",    392, 0, "Sen",          "Japanese Yen", "JP"),
    "KES" -> ("Sh",   404, 2, "Cent",         "Kenyan Shilling", "KE"),
    "KGS" -> ("",     417, 2, "Tyiyn",        "Som", "KG"),
    "KHR" -> ("",     116, 0, "Sen",          "Riel", "KH"),
    "KMF" -> ("Fr",   174, 0, "Centime",      "Comoro Franc", "KM"),
    "KPW" -> ("₩",    408, 0, "Chŏn",         "North Korean Won", "KP"),
    "KRW" -> ("₩",    410, 0, "Jeon",         "South Korean Won", "KR"),
    "KWD" -> ("",     414, 3, "Fils",         "Kuwaiti Dinar", "KW"),
    "KYD" -> ("$",    136, 2, "Cent",         "Cayman Islands Dollar", "KY"),
    "KZT" -> ("〒",   398, 2, "Tiyn",         "Tenge", "KZ"),
    "LAK" -> ("₭",    418, 0, "Att",          "Kip", "LA"),
    "LBP" -> ("",     422, 0, "Piastre",      "Lebanese Pound", "LB"),
    "LKR" -> ("Rs",   144, 2, "Cent",         "Sri Lanka Rupee", "LK"),
    "LRD" -> ("$",    430, 2, "Cent",         "Liberian Dollar", "LR"),
    "LSL" -> ("L",    426, 2, "Sente",        "Lesotho Loti", "LS"),
    "LTL" -> ("Lt",   440, 2, "Centas",       "Lithuanian Litas", "LT"),
    "LVL" -> ("Ls",   428, 2, "Santīms",      "Latvian Lats", "LV"),
    "LYD" -> ("",     434, 3, "Dirham",       "Libyan Dinar", "LY"),
    "MAD" -> ("",     504, 2, "Centime",      "Moroccan Dirham", "MA"),
    "MDL" -> ("L",    498, 2, "Ban",          "Moldovan Leu", "MD"),
    "MGA" -> ("",     969, 1, "Iraimbilanja", "Malagasy Ariary", "MG"),
    "MKD" -> ("ден",  807, 2, "Deni",         "Denar", "MK"),
    "MMK" -> ("K",    104, 0, "Pya",          "Kyat", "MM"),
    "MNT" -> ("₮",    496, 2, "Möngö",        "Tugrik", "MN"),
    "MOP" -> ("",     446, 1, "Penny",        "Pataca", "MO"),
    "MRO" -> ("P",    478, 1, "Avo",          "Ouguiya", "MR"),
    "MUR" -> ("₨",    480, 2, "Cent",         "Mauritius Rupee", "MU"),
    "MVR" -> ("",     462, 2, "Lari",         "Rufiyaa", "MV"),
    "MWK" -> ("MK",   454, 2, "Tambala",      "Kwacha", "MW"),
    "MXN" -> ("$",    484, 2, "Centavo",      "Mexican Peso", "MX"),
    "MYR" -> ("RM",   458, 2, "Sen",          "Malaysian Ringgit", "MY"),
    "MZN" -> ("MTn",  943, 2, "Centavo",      "Metical", "MZ"),
    "NAD" -> ("$",    516, 2, "Cent",         "Namibian Dollar", "NA"),
    "NGN" -> ("₦",    566, 2, "Kobo",         "Naira", "NG"),
    "NIO" -> ("C$",   558, 2, "Centavo",      "Cordoba Oro", "NI"),
    "NOK" -> ("kr",   578, 2, "Øre",          "Norwegian Krone", "NO"),
    "NPR" -> ("₨",    524, 2, "Paisa",        "Nepalese Rupee", "NP"),
    "NZD" -> ("$",    554, 2, "Cent",         "New Zealand Dollar", "NZ"),
    "OMR" -> ("",     512, 3, "Baisa",        "Rial Omani", "OM"),
    "PAB" -> ("B/.",  590, 2, "Centésimo",    "Balboa", "PA"),
    "PEN" -> ("S/.",  604, 2, "Céntimo",      "Nuevo Sol", "PE"),
    "PGK" -> ("K",    598, 2, "Toea",         "Kina Papua", "PG"),
    "PHP" -> ("₱",    608, 2, "Centavo",      "Philippine Peso", "PH"),
    "PKR" -> ("₨",    586, 2, "Paisa",        "Pakistan Rupee", "PK"),
    "PLN" -> ("zł",   985, 2, "Grosz",        "Złoty", "PL"),
    "PYG" -> ("₲",    600, 0, "Céntimo",      "Guarani", "PY"),
    "QAR" -> ("",     634, 2, "Dirham",       "Qatari Rial", "QA"),
    "RON" -> ("L",    946, 2, "Ban",          "Romanian Leu", "RO"),
    "RSD" -> ("din.", 941, 2, "Para",         "Serbian Dinar", "RS"),
    "RUB" -> ("р.",   643, 2, "Kopek",        "Russian Rouble", "RU"),
    "RWF" -> ("Fr",   646, 0, "Centime",      "Rwanda Franc", "RW"),
    "SAR" -> ("",     682, 2, "Hallallah",    "Saudi Riyal", "SA"),
    "SBD" -> ("$",     90, 2, "Cent",         "Solomon Islands Dollar", "SB"),
    "SCR" -> ("₨",    690, 2, "Cent",         "Seychelles Rupee", "SC"),
    "SDG" -> ("£",    938, 2, "Piastre",      "Sudanese Pound", "SD"),
    "SEK" -> ("kr",   752, 2, "Öre",          "Swedish Krona", "SE"),
    "SGD" -> ("$",    702, 2, "Cent",         "Singapore Dollar", "SG"),
    "SHP" -> ("£",    654, 2, "Penny",        "Saint Helena Pound", "SH"),
    "SLL" -> ("Le",   694, 0, "Cent",         "Leone", "SL"),
    "SOS" -> ("Sh",   706, 2, "Cent",         "Somali Shilling", "SO"),
    "SRD" -> ("$",    968, 2, "Cent",         "Surinam Dollar", "SR"),
    "STD" -> ("Db",   678, 0, "Cêntimo",      "Dobra", "ST"),
    "SYP" -> ("£",    760, 2, "Piastre",      "Syrian Pound", "SY"),
    "SZL" -> ("L",    748, 2, "Cent",         "Lilangeni", "SZ"),
    "THB" -> ("฿",    764, 2, "Satang",       "Baht", "TH"),
    "TJS" -> ("ЅМ",   972, 2, "Diram",        "Somoni", "TJ"),
    "TMM" -> ("m",    934, 2, "Tennesi",      "Manat", "TM"),
    "TND" -> ("",     788, 3, "Millime",      "Tunisian Dinar", "TN"),
    "TOP" -> ("T$",   776, 2, "Seniti",       "Pa'anga", "TO"),
    "TRY" -> ("₤",    949, 2, "kuruş",        "Turkish Lira", "TR"),
    "TTD" -> ("$",    780, 2, "Cent",         "Trinidad and Tobago Dollar", "TT"),
    "TWD" -> ("$",    901, 1, "Cent",         "New Taiwan Dollar", "TW"),
    "TZS" -> ("Sh",   834, 2, "Cent",         "Tanzanian Shilling", "TZ"),
    "UAH" -> ("₴",    980, 2, "Kopiyka",      "Hryvnia", "UA"),
    "UGX" -> ("Sh",   800, 0, "Cent",         "Uganda Shilling", "UG"),
    "USD" -> ("$",    840, 2, "Cent",         "Dollar", "US"),
    "UYU" -> ("$",    858, 2, "Centésimo",    "Peso Uruguayo", "UY"),
    "UZS" -> ("",     860, 2, "Tiyin",        "Uzbekistan Som", "UZ"),
    "VEF" -> ("Bs F", 937, 2, "Céntimo",      "Venezuelan Bolívar", "VE"),
    "VND" -> ("₫",    704, 0, "Hào",          "Vietnamese Dồng", "VN"),
    "VUV" -> ("Vt",   548, 0, "",             "Vatu", "VU"),
    "WST" -> ("T",    882, 2, "Sene",         "Samoan Tala", "WS"),
    "XAF" -> ("Fr",   950, 0, "Centime",      "Central African CFA Franc", "CM,CF,CD,GQ,GA,TD"),
    "XCD" -> ("$",    951, 2, "Cent",         "East Caribbean Dollar", "AG,DM,GD,KN,LC,VC"),
    "XOF" -> ("Fr",   952, 0, "Centime",      "CFA Franc BCEAO", "BJ,BF,CI,GW,ML,NE,SN,TG"),
    "XPF" -> ("Fr",   953, 0, "Centime",      "CFP Franc", "PF"),
    "YER" -> ("",     886, 0, "Fils",         "Yemeni Rial", "YE"),
    "ZAR" -> ("R",    710, 2, "Cent",         "South African Rand", "ZA"),
    "ZMK" -> ("ZK",   894, 0, "Ngwee",        "Kwacha", "ZM"),
    "ZWR" -> ("$",    932, 2, "Cent",         "Zimbabwe Dollar", "ZW")
  )

/* Verfies that the given currency code is in the currency list.
 */
  private def checkCurrencyCode(currencyCode: String): String =
    if (currencyCode.isEmpty)
      ""
    else if (currencies.contains(currencyCode))
      currencyCode
    else
      throw new UnknownCurrencyException

/** Returns the currency symbol for the specified currency.
  * For example, returns $ for USD, kr for Swedish Krona, Rs for Indian 
  * Rupees, etc.
  *
  * @param currencyCode ISO 4217 three-letter currency code.
  * @return currency symbol as string or empty string.
  * @throws UnknownCurrencyException if <code>currencyCode</code> 
  * is not valid.
  */
  def getSymbolFor(currencyCode: String): String =
    if (currencies.contains(currencyCode))
      currencies(currencyCode)._1
    else if (currencyCode.isEmpty)
      ""
    else
      throw new UnknownCurrencyException

/** Returns the ISO 4217 numeric code for the specified currency.
  *
  * @param currencyCode ISO 4217 three-letter currency code.
  * @return ISO 4217 numeric currency code.
  * @throws UnknownCurrencyException if <code>currencyCode</code> 
  * is not valid.
  */
  def getNumericCodeFor(currencyCode: String): Int =
    if (currencies.contains(currencyCode))
      currencies(currencyCode)._2
    else
      throw new UnknownCurrencyException

/** Returns the default number of fraction digits used with the specified currency.
  *
  * @param currencyCode ISO 4217 three-letter currency code.
  * @return number of decimal places for this currency.
  * @throws UnknownCurrencyException if <code>currencyCode</code> 
  * is not valid.
  */
  def getDecimalsFor(currencyCode: String): Int =
    if (currencies.contains(currencyCode))
      currencies(currencyCode)._3
    else if (currencyCode.isEmpty)
      2
    else
      throw new UnknownCurrencyException

/** Returns the English currency name for the fractional unit of the specified
  * currency, e.g. <i>Cent</i> for <i>Euro</i>, <i>Penny</i> for <i>Pound Sterling</i>,
  * etc. Always returns empty <code>String</code> for unknown currencies.
  *
  * @param currencyCode ISO 4217 three-letter currency code.
  * @return name of the currency.
  * @throws UnknownCurrencyException if <code>currencyCode</code> 
  * is not valid.
  */
  def getFractNameFor(currencyCode: String): String  =
    if (currencies.contains(currencyCode))
      currencies(currencyCode)._4
    else if (currencyCode.isEmpty)
      ""
    else
      throw new UnknownCurrencyException

/** Returns the English currency name for the specified currency.
  *
  * @param currencyCode ISO 4217 three-letter currency code.
  * @return name of the currency.
  * @throws UnknownCurrencyException if <code>currencyCode</code> 
  * is not valid.
  */
  def getNameFor(currencyCode: String): String  =
    if (currencies.contains(currencyCode))
      currencies(currencyCode)._5
    else if (currencyCode.isEmpty)
      ""
    else
      throw new UnknownCurrencyException

/** Returns one or more two-letter ISO 3166-1 country code(s) that indicate where
  * the specified currency is in use.
  *
  * @param currencyCode ISO 4217 three-letter currency code.
  * @return Array of String with one or more country code(s).
  * @throws UnknownCurrencyException if <code>currencyCode</code> 
  * is not valid.
  */
  def getCountriesFor(currencyCode: String): Array[String] =
    if (currencies.contains(currencyCode))
      currencies(currencyCode)._6.split(",")
    else
      throw new UnknownCurrencyException

/** Returns a <code>Set</code> containing all 3-letter ISO currency codes as
  * Strings.
  *
  * @return all currency codes.
  */
  def getAllCurrencies = currencies.keySet

/* ----- FACTORY METHODS (String) ----- */

/** Constructs a <code>Currency</code> value from a numeric <code>String</code> value.
  *
  * @param amount specified amount as <code>String</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @param decimals number of decimal places.
  * @param roundingMode rounding mode to apply.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: String, currencyCode: String, decimals: Int, roundingMode: RoundingMode): Currency =
    new Currency(new BigDec(amount), checkCurrencyCode(currencyCode), decimals, roundingMode)

/** Constructs a <code>Currency</code> value from a numeric <code>String</code> value
  * with default rounding mode (<code>ROUND_HALF_UP</code>).
  *
  * @param amount specified amount as <code>String</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @param decimals number of decimal places.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: String, currencyCode: String, decimals: Int): Currency =
    new Currency(new BigDec(amount), checkCurrencyCode(currencyCode), decimals, ROUND_HALF_UP)

/** Constructs a <code>Currency</code> value from a numeric <code>String</code> value
  * with default rounding mode (<code>ROUND_HALF_UP</code>) and default number of
  * decimal places. The number of decimal places is determined by the designated
  * currency (e.g. 2 for most currencies, 1 for Chinese Yuan, 0 for Colombian Peso, etc.)
  *
  * @param amount specified amount as <code>String</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: String, currencyCode: String): Currency =
    new Currency(new BigDec(amount), checkCurrencyCode(currencyCode), getDecimalsFor(currencyCode), ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a numeric <code>String</code> value.
  *
  * @param amount specified amount as <code>String</code> value.
  */
  def apply(amount: String): Currency =
    new Currency(new BigDec(amount), "", getDecimalsFor(""), ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a numeric <code>String</code> value
  * with the given number of decimal places.
  *
  * @param amount specified amount as <code>String</code> value.
  * @param decimals number of decimal places.
  */
  def apply(amount: String, decimals: Int): Currency =
    new Currency(new BigDec(amount), "", decimals, ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a numeric <code>String</code> value
  * with the given rounding mode.
  *
  * @param amount specified amount as <code>String</code> value.
  * @param roundingMode rounding mode to apply.
  */
  def apply(amount: String, roundingMode: RoundingMode): Currency =
    new Currency(new BigDec(amount), "", getDecimalsFor(""), roundingMode)

/** Constructs a non-specific <code>Currency</code> value from a numeric <code>String</code> value
  * with the given number of decimal places and the given rounding mode.
  *
  * @param amount specified amount as <code>String</code> value.
  * @param decimals number of decimal places.
  * @param roundingMode rounding mode to apply.
  */
  def apply(amount: String, decimals: Int, roundingMode: RoundingMode): Currency =
    new Currency(new BigDec(amount), "", decimals, roundingMode)

/* ----- FACTORY METHODS (Double) ----- */

/** Constructs a <code>Currency</code> value from a <code>Double</code> value.
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @param decimals number of decimal places.
  * @param roundingMode rounding mode to apply.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: Double, currencyCode: String, decimals: Int, roundingMode: RoundingMode): Currency =
    new Currency(BigDec.valueOf(amount), checkCurrencyCode(currencyCode), decimals, roundingMode)

/** Constructs a <code>Currency</code> value from a <code>Double</code> value
  * with default rounding mode (<code>ROUND_HALF_UP</code>).
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @param decimals number of decimal places.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: Double, currencyCode: String, decimals: Int): Currency =
    new Currency(BigDec.valueOf(amount), checkCurrencyCode(currencyCode), decimals, ROUND_HALF_UP)

/** Constructs a <code>Currency</code> value from a <code>Double</code> value
  * with default rounding mode (<code>ROUND_HALF_UP</code>) and default number 
  * of decimal places. The number of decimal places is determined by the designated
  * currency (e.g. 2 for most currencies, 1 for Chinese Yuan, 0 for Colombian Peso, etc.)
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: Double, currencyCode: String): Currency =
    new Currency(BigDec.valueOf(amount), checkCurrencyCode(currencyCode), getDecimalsFor(currencyCode), ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a <code>Double</code> value.
  *
  * @param amount specified amount as <code>Double</code> value.
  */
  def apply(amount: Double): Currency =
    new Currency(BigDec.valueOf(amount), "", getDecimalsFor(""), ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a <code>Double</code> value
  * with the given number of decimal places.
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param decimals number of decimal places.
  */
  def apply(amount: Double, decimals: Int): Currency =
    new Currency(BigDec.valueOf(amount), "", decimals, ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a <code>Double</code> value
  * with the given rounding mode.
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param roundingMode rounding mode to apply.
  */
  def apply(amount: Double, roundingMode: RoundingMode): Currency =
    new Currency(BigDec.valueOf(amount), "", getDecimalsFor(""), roundingMode)

/** Constructs a non-specific <code>Currency</code> value from a <code>Double</code> value
  * with the given number of decimal places and the given rounding mode.
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param decimals number of decimal places.
  * @param roundingMode rounding mode to apply.
  */
  def apply(amount: Double, decimals: Int, roundingMode: RoundingMode): Currency =
    new Currency(BigDec.valueOf(amount), "", decimals, roundingMode)

/* ----- FACTORY METHODS (Long) ----- */

/** Constructs a <code>Currency</code> value from a <code>Long</code> value.
  *
  * @param amount specified amount as <code>Long</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @param decimals number of decimal places.
  * @param roundingMode rounding mode to apply.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: Long, currencyCode: String, decimals: Int, roundingMode: RoundingMode): Currency =
    new Currency(BigDec.valueOf(amount), checkCurrencyCode(currencyCode), decimals, roundingMode)

/** Constructs a <code>Currency</code> value from a <code>Long</code> value
  * with default rounding mode (<code>ROUND_HALF_UP</code>).
  *
  * @param amount specified amount as <code>Long</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @param decimals number of decimal places.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: Long, currencyCode: String, decimals: Int): Currency =
    new Currency(BigDec.valueOf(amount), checkCurrencyCode(currencyCode), decimals, ROUND_HALF_UP)

/** Constructs a <code>Currency</code> value from a <code>Long</code> value
  * with default rounding mode (<code>ROUND_HALF_UP</code>) and default number of
  * decimal places. The number of decimal places is determined by the designated
  * currency (e.g. 2 for most currencies, 1 for Chinese Yuan, 0 for Colombian Peso, etc.)
  *
  * @param amount specified amount as <code>Long</code> value.
  * @param currencyCode ISO 4217 three-letter currency code.
  * @throws UnknownCurrencyException if currency code is not recognised.
  */
  def apply(amount: Long, currencyCode: String): Currency =
    new Currency(BigDec.valueOf(amount), checkCurrencyCode(currencyCode), getDecimalsFor(currencyCode), ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a <code>Long</code> value.
  *
  * @param amount specified amount as <code>Long</code> value.
  */
  def apply(amount: Long): Currency =
    new Currency(BigDec.valueOf(amount), "", getDecimalsFor(""), ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a <code>Long</code> value
  * with the given number of decimal places.
  *
  * @param amount specified amount as <code>Long</code> value.
  * @param decimals number of decimal places.
  */
  def apply(amount: Long, decimals: Int): Currency =
    new Currency(BigDec.valueOf(amount), "", decimals, ROUND_HALF_UP)

/** Constructs a non-specific <code>Currency</code> value from a <code>Long</code> value
  * with the given rounding mode.
  *
  * @param amount specified amount as <code>Double</code> value.
  * @param roundingMode rounding mode to apply.
  */
  def apply(amount: Long, roundingMode: RoundingMode): Currency =
    new Currency(BigDec.valueOf(amount), "", getDecimalsFor(""), roundingMode)

/** Constructs a non-specific <code>Currency</code> value from a <code>Long</code> value
  * with the given number of decimal places and the given rounding mode.
  *
  * @param amount specified amount as <code>Long</code> value.
  * @param decimals number of decimal places.
  * @param roundingMode rounding mode to apply.
  */
  def apply(amount: Long, decimals: Int, roundingMode: RoundingMode): Currency =
    new Currency(BigDec.valueOf(amount), "", decimals, roundingMode)

}
