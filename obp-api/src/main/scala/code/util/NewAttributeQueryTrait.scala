package code.util

import code.api.util.DoobieQueries
import com.openbankproject.commons.model.BankId
import net.liftweb.mapper.{BaseMappedField, BaseMetaMapper}

import scala.collection.immutable.List

/**
 * Any Attribute type Mapped entity companion object extends this trait, will obtain query with parameter function: getParentIdByParams
 */
trait NewAttributeQueryTrait {
  self: BaseMetaMapper =>
  private lazy val tableName = self.dbTableName
  // TODO Should we rename this column to attributeName
  private lazy val nameColumn = Name.dbColumnName
  // TODO Should we rename this column to attributeValue
  private lazy val valueColumn = `Value`.dbColumnName
  private lazy val parentIdColumn = ParentId.dbColumnName
  private lazy val bankIdColumn = BankId.dbColumnName
  val BankId: BaseMappedField
  val Name: BaseMappedField
  val `Value`: BaseMappedField
  /**
   * Attribute entity's parent id, for example: CustomerAttribute.customerId,
   * need implemented in companion object
   */
  val ParentId: BaseMappedField

  /**
   * query attribute's parent id, according request params
   *
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

      val parentIdToNameValues: Map[String, Map[String, String]] = parentIdToAttributes.mapValues(rows => {
        rows.map { case (_, name, value) =>
          name -> value
        }.toMap
      })

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
