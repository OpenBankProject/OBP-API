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

package code.api.util

import com.openbankproject.commons.model.enums.AttributeType

/**
 * This object holds the one description of the values an attribute's `type` field accepts, for
 * every kind of attribute (Bank, Customer, Product, Account, Card, Transaction, Transaction Request,
 * User, ATM, Counterparty, Regulated Entity) and for Attribute Definitions.
 *
 * Each kind of attribute has its own enumeration in obp-commons (`ProductAttributeType`,
 * `AccountAttributeType` and so on), but they all accept the same names. The error messages and
 * ResourceDoc descriptions used to list those names by hand, in more than forty places, and the
 * copies had already drifted apart. They now all read from here. `AttributeTypeDocsTest` checks that
 * every enumeration still has exactly the names listed in [[examples]].
 *
 * An attribute's value is always stored as text; the type says how the value should be read.
 */
object AttributeTypeDocs {

  /**
   * One example value for each attribute type, in the order they are presented to callers.
   * DECIMAL is an exact decimal number, which is what rates, prices and face values need: a DOUBLE
   * is a binary floating point number and cannot represent most decimal fractions exactly.
   */
  val examples: List[(AttributeType.Value, String)] = List(
    AttributeType.STRING        -> "TAX_NUMBER",
    AttributeType.INTEGER       -> "123",
    AttributeType.DOUBLE        -> "12.1234",
    AttributeType.DECIMAL       -> "1234.5678",
    AttributeType.BOOLEAN       -> "true",
    AttributeType.DATE_WITH_DAY -> "2012-04-23"
  )

  /** The accepted types with an example of each, for error messages, e.g. `STRING(TAX_NUMBER), ... and DATE_WITH_DAY(2012-04-23)`. */
  val typesWithExamples: String = joinWithAnd(examples.map { case (attributeType, example) => s"$attributeType($example)" })

  /** A sentence for ResourceDoc descriptions that says which values the `type` field accepts and when to use DECIMAL. */
  val typeFieldDescription: String =
    s"The type field must be one of ${joinWithAnd(examples.map { case (attributeType, _) => s""""$attributeType"""" })}. " +
      s"""Use "${AttributeType.DECIMAL}" rather than "${AttributeType.DOUBLE}" for rates, prices and amounts, because it holds the value exactly; """ +
      s""""${AttributeType.BOOLEAN}" values are "true" or "false"."""

  private def joinWithAnd(items: List[String]): String =
    if (items.size <= 1) items.mkString else items.init.mkString(", ") + " and " + items.last
}
