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

package code.util

import code.api.util.DoobieQueries
import com.openbankproject.commons.model.BankId
import net.liftweb.mapper.{BaseMappedField, BaseMetaMapper}

import scala.collection.immutable.List

/**
 * Any Attribute type Mapped entity companion object extends this trait, will obtain query with parameter function: getParentIdByParams
 */
trait AttributeQueryTrait { self: BaseMetaMapper =>
  val mBankId: BaseMappedField

  val mName: BaseMappedField

  val mValue: BaseMappedField

  /**
   * Attribute entity's parent id, for example: CustomerAttribute.customerId,
   * need implemented in companion object
   */
  val mParentId: BaseMappedField


  private lazy val tableName = self.dbTableName
  // TODO Should we rename this column to attributeName
  private lazy val nameColumn = mName.dbColumnName
  // TODO Should we rename this column to attributeValue
  private lazy val valueColumn = mValue.dbColumnName
  private lazy val parentIdColumn = mParentId.dbColumnName
  private lazy val bankIdColumn = mBankId.dbColumnName

  /**
   * query attribute's parent id, according request params
   * @param bankId bankId
   * @param params request parameters
   * @return parentId list
   */
  def getParentIdByParams(bankId: BankId, params: Map[String, List[String]]): List[String] = {
    if (params.isEmpty) {
      // Use Doobie for type-safe query with proper JDBC type handling (including SQL Server NVARCHAR)
      DoobieQueries.getDistinctParentIds(tableName, parentIdColumn, bankIdColumn, bankId.value)
    } else {
      // Use Doobie for type-safe query with proper JDBC type handling (including SQL Server NVARCHAR)
      val results: List[(String, String, String)] = DoobieQueries.getParentIdWithAttributes(
        tableName, parentIdColumn, nameColumn, valueColumn, bankIdColumn, bankId.value, params
      )

      // Group by parentId and filter
      val parentIdToAttributes: Map[String, List[(String, String, String)]] = results.groupBy(_._1)

      val parentIdToNameValues: Map[String, Map[String, String]] = parentIdToAttributes.map { case (parentId, rows) =>
        parentId -> rows.map { case (_, name, value) =>
          name -> value
        }.toMap
      }

      for {
        (parentId, attributes: Map[String, String]) <- parentIdToNameValues.toList
        // check whether all nameValues's name and at lest on of values can be found in current parentId corresponding list of Attribute
        if (params.forall { kv =>
          val (parameterName, parameterValues) = kv
          attributes.get(parameterName).exists(parameterValues.contains(_))
        })
      } yield parentId
    }
  }
  
  def getSqlParametersFilter(paramList: List[(String, List[String])]): String = {
    paramList.map { kv =>
      val (_, values) = kv
      if (values.size == 1) {
        s"($nameColumn = ? AND $valueColumn = ?)"
      } else {
        //For lift framework not support in query, here just express in operation: mname = ? and mvalue in (?, ?, ?)
        val valueExp = values.map(_ => "?").mkString(", ")
        s"( $nameColumn = ? AND $valueColumn in ($valueExp) )"
      }
    }.mkString(" OR ")
  }
  def getParameters(paramList: List[(String, List[String])]): List[String] = {
    paramList.flatMap { kv =>
      val (name, values) = kv
      name :: values
    }
  }
  
}
