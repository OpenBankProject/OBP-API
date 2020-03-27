package code.api.attributedefinition

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import code.util.MappedUUID
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._

import scala.collection.immutable.List
import scala.concurrent.Future

object MappedAttributeDefinitionProvider extends AttributeDefinitionProviderTrait with MdcLoggable {
  def createOrUpdateAttributeDefinition(bankId: BankId,
                                        name: String,
                                        category: AttributeCategory.Value,
                                        `type`: AttributeType.Value,
                                        description: String,
                                        alias: String,
                                        canBeSeenOnViews: List[String],
                                        isActive: Boolean
                                       ): Future[Box[AttributeDefinition]] = Future {
    AttributeDefinition.find(
      By(AttributeDefinition.BankId, bankId.value),
      By(AttributeDefinition.Name, name),
      By(AttributeDefinition.Category, category.toString)
    ) match {
      case Full(attributeDefinition) =>
        Full(
          attributeDefinition
            .BankId(bankId.value)
            .Name(name)
            .Category(category.toString)
            .`TypeOfValue`(`type`.toString)
            .Description(description)
            .Alias(alias)
            .CanBeSeenOnViews(canBeSeenOnViews.mkString(";"))
            .IsActive(isActive)
            .saveMe()
        )
      case Empty =>
        Full(
          AttributeDefinition.create
            .BankId(bankId.value)
            .Name(name)
            .Category(category.toString)
            .`TypeOfValue`(`type`.toString)
            .Description(description)
            .Alias(alias)
            .CanBeSeenOnViews(canBeSeenOnViews.mkString(";"))
            .IsActive(isActive)
            .saveMe()
        )
      case someError => someError
    }

  }

  def deleteAttributeDefinition(attributeDefinitionId: String, 
                                   category: AttributeCategory.Value): Future[Box[Boolean]] = Future {
    AttributeDefinition.find(
      By(AttributeDefinition.AttributeDefinitionId, attributeDefinitionId),
      By(AttributeDefinition.Category, category.toString)
    ) match {
      case Full(attribute) => Full(attribute.delete_!)
      case Empty           => Empty ?~! ErrorMessages.AttributeNotFound
      case unhandledError  => 
        logger.error(unhandledError)
        Full(false)
    }
  }

  def getAttributeDefinition(category: AttributeCategory.Value): Future[Box[List[AttributeDefinition]]] = Future {
    Full(
      AttributeDefinition.findAll(
        By(AttributeDefinition.Category, category.toString)
      )
    )
  }
  
}

class AttributeDefinition extends AttributeDefinitionTrait with LongKeyedMapper[AttributeDefinition] with IdPK with CreatedUpdated {
  override def getSingleton = AttributeDefinition
  object AttributeDefinitionId extends MappedUUID(this)
  object BankId extends MappedString(this, 50)
  object Name extends MappedString(this, 50)
  object Category extends MappedString(this, 50)
  object `TypeOfValue` extends MappedString(this, 50)
  object Description extends MappedString(this, 256)
  object Alias extends MappedString(this, 50)
  object CanBeSeenOnViews extends MappedString(this, 256)
  object IsActive extends MappedBoolean(this)

  import com.openbankproject.commons.model.{BankId => BankIdCommonModel}
  def attributeDefinitionId: String = AttributeDefinitionId.get
  def bankId: BankIdCommonModel = BankIdCommonModel(BankId.get)
  def name: String = Name.get
  def category: AttributeCategory.Value = AttributeCategory.withName(Category.get)
  def `type`: AttributeType.Value = AttributeType.withName(`TypeOfValue`.get)
  def description: String = Description.get
  def alias: String = Alias.get
  def canBeSeenOnViews: List[String] = CanBeSeenOnViews.get.split(";").toList
  def isActive: Boolean = IsActive.get

}

object AttributeDefinition extends AttributeDefinition with LongKeyedMetaMapper[AttributeDefinition] {
  override def dbIndexes: List[BaseIndex[AttributeDefinition]] = UniqueIndex(BankId, Name, Category) :: super.dbIndexes
}
