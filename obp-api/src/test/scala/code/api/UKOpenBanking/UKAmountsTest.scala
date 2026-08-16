package code.api.UKOpenBanking

import code.api.util.OBPTransactionDirection
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

/**
 * UK Open Banking splits a signed amount in two: `Amount` is unsigned (its pattern,
 * `^\d{1,13}$|^\d{1,13}\.\d{1,5}$`, admits no sign) and the direction sits beside it in
 * `CreditDebitIndicator`. OBP holds one signed BigDecimal, so every UK response has to split it.
 *
 * The factories used to hardcode `"Credit"` and pass the signed number straight through, which
 * reported a debit of 25 as a credit of -25 — both halves wrong at once. These scenarios pin the
 * split so neither half can drift back.
 */
class UKAmountsTest extends AnyFeatureSpec with Matchers with GivenWhenThen {

  Feature("UK Open Banking - splitting a signed amount into magnitude and direction") {

    Scenario("a negative amount is a debit, reported as its magnitude") {
      UKAmounts.creditDebitIndicator(BigDecimal("-25.00")) should be("Debit")
      UKAmounts.unsignedAmount(BigDecimal("-25.00")) should be("25.00")
    }

    Scenario("a positive amount is a credit, unchanged") {
      UKAmounts.creditDebitIndicator(BigDecimal("1209.06")) should be("Credit")
      UKAmounts.unsignedAmount(BigDecimal("1209.06")) should be("1209.06")
    }

    Scenario("zero is a credit, as the standard states explicitly") {
      UKAmounts.creditDebitIndicator(BigDecimal(0)) should be("Credit")
      UKAmounts.unsignedAmount(BigDecimal(0)) should be("0")
    }

    Scenario("a missing amount is treated as zero, not as an error") {
      UKAmounts.creditDebitIndicator(None: Option[BigDecimal]) should be("Credit")
      UKAmounts.unsignedAmount(None: Option[BigDecimal]) should be("0")
      UKAmounts.creditDebitIndicator(Some(BigDecimal("-1"))) should be("Debit")
      UKAmounts.unsignedAmount(Some(BigDecimal("-1"))) should be("1")
    }

    Scenario("an amount OBP already holds as a string splits the same way") {
      UKAmounts.creditDebitIndicatorOfString("-25.00") should be("Debit")
      UKAmounts.unsignedAmountString("-25.00") should be("25.00")
      UKAmounts.creditDebitIndicatorOfString("1209.06") should be("Credit")
      UKAmounts.unsignedAmountString("1209.06") should be("1209.06")
    }

    Scenario("a value that is not a number is passed through rather than turned into a fabricated zero") {
      UKAmounts.unsignedAmountString("") should be("")
      UKAmounts.unsignedAmountString("not-a-number") should be("not-a-number")
      UKAmounts.creditDebitIndicatorOfString("") should be("Credit")
    }

    Scenario("granting both directions, or neither, restricts nothing") {
      // Neither is the plain ReadTransactionsBasic/Detail case; both is a TPP asking for everything.
      for (amount <- List(BigDecimal("-25"), BigDecimal("25"), BigDecimal(0))) {
        UKAmounts.admitsDirection(Some(amount), grantsCredits = false, grantsDebits = false) should be(true)
        UKAmounts.admitsDirection(Some(amount), grantsCredits = true, grantsDebits = true) should be(true)
      }
    }

    Scenario("granting only Credits admits credits and excludes debits") {
      UKAmounts.admitsDirection(Some(BigDecimal("25")), grantsCredits = true, grantsDebits = false) should be(true)
      UKAmounts.admitsDirection(Some(BigDecimal("-25")), grantsCredits = true, grantsDebits = false) should be(false)
      // Zero is a credit, so a Credits-only consent sees it.
      UKAmounts.admitsDirection(Some(BigDecimal(0)), grantsCredits = true, grantsDebits = false) should be(true)
    }

    Scenario("granting only Debits admits debits and excludes credits") {
      UKAmounts.admitsDirection(Some(BigDecimal("-25")), grantsCredits = false, grantsDebits = true) should be(true)
      UKAmounts.admitsDirection(Some(BigDecimal("25")), grantsCredits = false, grantsDebits = true) should be(false)
      UKAmounts.admitsDirection(Some(BigDecimal(0)), grantsCredits = false, grantsDebits = true) should be(false)
    }

    Scenario("what a response labels Debit is what a Debits-only consent admits") {
      // The two must agree, or a row could be labelled one direction and filtered as the other.
      for (amount <- List(BigDecimal("-0.01"), BigDecimal("0"), BigDecimal("0.01"), BigDecimal("-1000"))) {
        val labelledDebit = UKAmounts.creditDebitIndicator(amount) == "Debit"
        UKAmounts.admitsDirection(Some(amount), grantsCredits = false, grantsDebits = true) should be(labelledDebit)
        UKAmounts.admitsDirection(Some(amount), grantsCredits = true, grantsDebits = false) should be(!labelledDebit)
      }
    }

    Scenario("a scale that would render in scientific notation still comes out plain") {
      // BigDecimal("1E+3").toString is "1E+3", which the Amount pattern rejects.
      UKAmounts.unsignedAmount(BigDecimal("1E+3")) should be("1000")
      UKAmounts.unsignedAmount(BigDecimal("-1E+3")) should be("1000")
      UKAmounts.unsignedAmountString("1E+3") should be("1000")
    }

    Scenario("the query restriction matches the directions granted") {
      // Both or neither is no restriction, so no param is added at all.
      UKAmounts.directionQueryParam(grantsCredits = true, grantsDebits = true) should be(Nil)
      UKAmounts.directionQueryParam(grantsCredits = false, grantsDebits = false) should be(Nil)
      UKAmounts.directionQueryParam(grantsCredits = true, grantsDebits = false) should
        be(List(OBPTransactionDirection(credits = true)))
      UKAmounts.directionQueryParam(grantsCredits = false, grantsDebits = true) should
        be(List(OBPTransactionDirection(credits = false)))
    }

    Scenario("the query restriction and the post-filter agree on every amount") {
      // They are two enforcements of one rule -- the database narrows, the filter is authoritative.
      // If they disagreed, a row could be selected by one and dropped by the other.
      //
      // The boundary comes from OBPTransactionDirection -- the same value LocalMappedConnector
      // builds its SQL predicate from -- rather than a copy written here. A local copy agrees with
      // itself no matter what the connector does, so it could not detect the drift it exists to
      // catch.
      for (amount <- List(BigDecimal("-1000"), BigDecimal("-0.01"), BigDecimal(0), BigDecimal("0.01"))) {
        for ((credits, debits) <- List((true, false), (false, true))) {
          val smallestUnit = (amount * 100).toLongExact
          val queryWouldKeep = UKAmounts.directionQueryParam(credits, debits) match {
            case List(param) => OBPTransactionDirection.admits(param, smallestUnit)
            case _ => true
          }
          withClue(s"amount $amount credits=$credits debits=$debits: ") {
            UKAmounts.admitsDirection(Some(amount), credits, debits) should be(queryWouldKeep)
          }
        }
      }
    }

    Scenario("an amount the view withheld is admitted by neither direction") {
      // None here is "the moderating view did not grant CAN_SEE_TRANSACTION_AMOUNT", not "zero".
      // creditDebitIndicator still labels it Credit for rendering, but as a permission test that
      // would hand every debit to a Credits-only consent, so the restriction must refuse instead.
      UKAmounts.admitsDirection(None, grantsCredits = true, grantsDebits = false) should be(false)
      UKAmounts.admitsDirection(None, grantsCredits = false, grantsDebits = true) should be(false)
      // With no restriction in force there is nothing to refuse.
      UKAmounts.admitsDirection(None, grantsCredits = true, grantsDebits = true) should be(true)
      UKAmounts.admitsDirection(None, grantsCredits = false, grantsDebits = false) should be(true)
    }

    Scenario("every produced Amount matches the standard's unsigned pattern") {
      val pattern = "^\\d{1,13}$|^\\d{1,13}\\.\\d{1,5}$".r
      List("-25.00", "25.00", "0", "-0.5", "1209.06", "-1234567890123").foreach { input =>
        val produced = UKAmounts.unsignedAmountString(input)
        withClue(s"input $input produced $produced: ") {
          pattern.findFirstIn(produced).isDefined should be(true)
        }
      }
    }
  }
}
