package code.api.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The empty literals in a statement become the sentinel; everything else in it is left as written. */
class OracleEmptyStringsTest extends AnyFlatSpec with Matchers {

  import OracleEmptyStrings.rewriteSql

  "an empty literal" should "become CHR(0)" in {
    rewriteSql("SELECT * FROM t WHERE bankid = '' AND x = 1") should equal("SELECT * FROM t WHERE bankid = CHR(0) AND x = 1")
    rewriteSql("COALESCE(a, '')") should equal("COALESCE(a, CHR(0))")
    rewriteSql("VALUES ('', '')") should equal("VALUES (CHR(0), CHR(0))")
  }

  "a literal with text in it" should "be left alone, escaped quotes included" in {
    rewriteSql("WHERE name = 'it''s'") should equal("WHERE name = 'it''s'")
    rewriteSql("WHERE a = '''' AND b = ''") should equal("WHERE a = '''' AND b = CHR(0)")
    rewriteSql("WHERE a = 'x' || ''") should equal("WHERE a = 'x' || CHR(0)")
  }

  "identifiers and comments" should "not be read as literals" in {
    rewriteSql("SELECT \"a''b\" FROM t -- don't\nWHERE c = ''") should equal("SELECT \"a''b\" FROM t -- don't\nWHERE c = CHR(0)")
    rewriteSql("/* it's '' */ SELECT ''") should equal("/* it's '' */ SELECT CHR(0)")
  }

  "a statement without an empty literal" should "come back unchanged" in {
    rewriteSql("SELECT 1 FROM dual") should equal("SELECT 1 FROM dual")
    rewriteSql(null) should equal(null)
  }
}
