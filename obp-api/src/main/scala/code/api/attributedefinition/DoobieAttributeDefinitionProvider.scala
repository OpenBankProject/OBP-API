package code.api.attributedefinition

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import com.openbankproject.commons.model.{BankId => BankIdCommonModel}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}

import scala.collection.immutable.List
import scala.concurrent.Future

/**
 * The row type keeps the name `AttributeDefinition` that the Lift entity had.
 *
 * That name is not local: it appears in Connector.scala's method signatures, in
 * LocalMappedConnector's overrides, in NewStyle's AttributeDocumentation wrapper and in
 * JSONFactory4.0.0 - a public-interface leak of the concrete entity type. Keeping the name means
 * none of those signatures change and the migration stays a swap of the storage layer rather
 * than a rename rippling through the connector API.
 */
case class AttributeDefinition(
  attributeDefinitionId: String,
  bankId: BankIdCommonModel,
  name: String,
  category: AttributeCategory.Value,
  `type`: AttributeType.Value,
  description: String,
  alias: String,
  canBeSeenOnViews: List[String],
  isActive: Boolean
) extends AttributeDefinitionTrait

object AttributeDefinition {

  private val selectColumns =
    fr"""SELECT attributedefinitionid, bankid, name, category, typeofvalue, description, alias,
                canbeseenonviews, isactive
         FROM attributedefinition"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Boolean])

  private def fromRow(row: Row): AttributeDefinition = row match {
    case (attributeDefinitionId, bankId, name, category, typeOfValue, description, alias, canBeSeenOnViews, isActive) =>
      AttributeDefinition(
        attributeDefinitionId = attributeDefinitionId.orNull,
        bankId = BankIdCommonModel(bankId.orNull),
        name = name.orNull,
        category = AttributeCategory.withName(category.orNull),
        `type` = AttributeType.withName(typeOfValue.orNull),
        description = description.orNull,
        alias = alias.orNull,
        // Mapper stored this as a ";"-joined string and read it back with a bare split, so an
        // empty column yields List("") rather than Nil. Preserved: callers filter this list by
        // membership, and the empty-string element is inert there, but changing the shape would
        // be a behaviour change smuggled in with a storage swap.
        canBeSeenOnViews = canBeSeenOnViews.orNull.split(";").toList,
        // MappedBoolean read a NULL column as false, never as the declared defaultValue.
        isActive = isActive.getOrElse(false))
  }

  /** All definitions in one category, across every bank. */
  def findAllByCategory(category: String): List[AttributeDefinition] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE category = $category").query[Row].to[List]
    ).map(fromRow)

  /** All definitions for one bank in one category. */
  def findAllByBankIdAndCategory(bankId: String, category: String): List[AttributeDefinition] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE bankid = $bankId AND category = $category").query[Row].to[List]
    ).map(fromRow)

  /** All definitions for any of several banks in one category (the Mapper ByList shape). */
  def findAllByBankIdsAndCategory(bankIds: List[String], category: String): List[AttributeDefinition] =
    if (bankIds.isEmpty) Nil
    else {
      val inFrag = Fragments.in(fr"bankid", cats.data.NonEmptyList.fromListUnsafe(bankIds.distinct))
      DoobieUtil.runQuery(
        (selectColumns ++ fr"WHERE " ++ inFrag ++ fr" AND category = $category").query[Row].to[List]
      ).map(fromRow)
    }

  def findByAttributeDefinitionId(attributeDefinitionId: String): Box[AttributeDefinition] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE attributedefinitionid = $attributeDefinitionId").query[Row].option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

  def deleteByAttributeDefinitionId(attributeDefinitionId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM attributedefinition WHERE attributedefinitionid = $attributeDefinitionId".update.run)
    true
  }
}

object DoobieAttributeDefinitionProvider extends AttributeDefinitionProviderTrait with MdcLoggable {

  private def findByNaturalKey(bankId: String, name: String, category: String): Box[AttributeDefinition] =
    DoobieUtil.runQuery(
      sql"""SELECT attributedefinitionid FROM attributedefinition
            WHERE bankid = $bankId AND name = $name AND category = $category"""
        .query[String].option
    ) match {
      case Some(id) => AttributeDefinition.findByAttributeDefinitionId(id)
      case None => Empty
    }

  override def createOrUpdateAttributeDefinition(bankId: BankIdCommonModel,
                                                 name: String,
                                                 category: AttributeCategory.Value,
                                                 `type`: AttributeType.Value,
                                                 description: String,
                                                 alias: String,
                                                 canBeSeenOnViews: List[String],
                                                 isActive: Boolean
                                                ): Future[Box[AttributeDefinition]] = Future {
    val viewsValue = canBeSeenOnViews.mkString(";")
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    findByNaturalKey(bankId.value, name, category.toString) match {
      case Full(existing) =>
        DoobieUtil.runUpdate(
          sql"""UPDATE attributedefinition
                SET typeofvalue = ${`type`.toString}, description = $description, alias = $alias,
                    canbeseenonviews = $viewsValue, isactive = $isActive, updatedat = $now
                WHERE attributedefinitionid = ${existing.attributeDefinitionId}"""
            .update.run)
        Full(existing.copy(
          `type` = `type`, description = description, alias = alias,
          canBeSeenOnViews = viewsValue.split(";").toList, isActive = isActive))
      case Empty =>
        val newId = APIUtil.generateUUID()
        DoobieUtil.runUpdate(
          sql"""INSERT INTO attributedefinition
                (attributedefinitionid, bankid, name, category, typeofvalue, description, alias,
                 canbeseenonviews, isactive, createdat, updatedat)
                VALUES
                ($newId, ${bankId.value}, $name, ${category.toString}, ${`type`.toString}, $description,
                 $alias, $viewsValue, $isActive, $now, $now)"""
            .update.run)
        Full(AttributeDefinition(
          attributeDefinitionId = newId, bankId = bankId, name = name, category = category,
          `type` = `type`, description = description, alias = alias,
          canBeSeenOnViews = viewsValue.split(";").toList, isActive = isActive))
      case someError => someError
    }
  }

  override def deleteAttributeDefinition(attributeDefinitionId: String,
                                         category: AttributeCategory.Value): Future[Box[Boolean]] = Future {
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM attributedefinition
            WHERE attributedefinitionid = $attributeDefinitionId AND category = ${category.toString}"""
        .query[Int].unique
    ) match {
      case count if count > 0 =>
        Full(AttributeDefinition.deleteByAttributeDefinitionId(attributeDefinitionId))
      case _ =>
        Empty ?~! ErrorMessages.AttributeNotFound
    }
  }

  override def getAttributeDefinition(category: AttributeCategory.Value): Future[Box[List[AttributeDefinition]]] = Future {
    Full(AttributeDefinition.findAllByCategory(category.toString))
  }
}
