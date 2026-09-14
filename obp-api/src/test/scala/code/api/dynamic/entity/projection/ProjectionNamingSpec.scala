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

package code.api.dynamic.entity.projection

import org.scalatest.{FlatSpec, Matchers}

class ProjectionNamingSpec extends FlatSpec with Matchers {

  "ProjectionNaming.tableName" should "be deterministic and length/charset safe" in {
    val a = ProjectionNaming.tableName(None, "ParcelOwnerVerification")
    a shouldBe ProjectionNaming.tableName(None, "ParcelOwnerVerification") // deterministic
    a should fullyMatch regex "[a-z0-9_]+".r
    a.length should be <= 63
    a should startWith("de_")
  }

  it should "distinguish system-level from bank-level entities of the same name" in {
    ProjectionNaming.tableName(None, "Parcel") should not be ProjectionNaming.tableName(Some("bankX"), "Parcel")
  }

  it should "distinguish different entity names" in {
    ProjectionNaming.tableName(None, "Parcel") should not be ProjectionNaming.tableName(None, "Owner")
  }

  "ProjectionNaming.columnName" should "be deterministic, safe and start with c_" in {
    val c = ProjectionNaming.columnName("price.amount")
    c shouldBe ProjectionNaming.columnName("price.amount")
    c should fullyMatch regex "[a-z0-9_]+".r
    c.length should be <= 63
    c should startWith("c_")
  }

  "ProjectionDDL.sqlColumnType" should "map DE scalar types to portable SQL types" in {
    ProjectionDDL.sqlColumnType("number") shouldBe "numeric"
    ProjectionDDL.sqlColumnType("integer") shouldBe "bigint"
    ProjectionDDL.sqlColumnType("boolean") shouldBe "boolean"
    ProjectionDDL.sqlColumnType("DATE_WITH_DAY") shouldBe "date"
    ProjectionDDL.sqlColumnType("string") shouldBe "text"
    ProjectionDDL.sqlColumnType("reference:Bank") shouldBe "text"
  }
}
