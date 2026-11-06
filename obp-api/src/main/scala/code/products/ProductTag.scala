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

package code.products

import com.openbankproject.commons.model.{BankId, ProductCode}
import net.liftweb.common.Box

// Product tags keyed by (bank_id, product_code, tag). No FK to MappedProduct so tags work for
// connector-sourced products that have no local row.
//
// The Lift ProductTag entity is gone: the table is owned by Liquibase and the queries live in
// DoobieProductTags. This object stays as the call site's entry point so callers did not have to
// change, and delegates.
object ProductTagsProvider {
  def getTags(bankId: BankId, productCode: ProductCode): List[String] =
    DoobieProductTags.getTags(bankId, productCode)

  def setTags(bankId: BankId, productCode: ProductCode, tags: List[String]): Box[List[String]] =
    DoobieProductTags.setTags(bankId, productCode, tags)

  def getProductCodesWithAllTags(bankId: BankId, tags: List[String]): Set[String] =
    DoobieProductTags.getProductCodesWithAllTags(bankId, tags)

  def getTagsByProductCodes(bankId: BankId, productCodes: List[String]): Map[String, List[String]] =
    DoobieProductTags.getTagsByProductCodes(bankId, productCodes)
}
