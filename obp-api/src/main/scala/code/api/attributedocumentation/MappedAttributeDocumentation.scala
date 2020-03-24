package code.api.attributedocumentation

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import code.util.MappedUUID
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._

import scala.concurrent.Future

object MappedAttributeDocumentationProvider extends AttributeDocumentationProviderTrait with MdcLoggable {
  def createOrUpdateAttributeDocumentation(bankId: BankId,
                                           name: String,
                                           category: AttributeCategory.Value,
                                           `type`: AttributeType.Value,
                                           description: String,
                                           alias: String,
                                           isActive: Boolean
                                          ): Future[Box[AttributeDocumentation]] = Future {
    AttributeDocumentation.find(
      By(AttributeDocumentation.BankId, bankId.value),
      By(AttributeDocumentation.Name, name),
      By(AttributeDocumentation.Category, category.toString)
    ) match {
      case Full(attributeDocumentation) =>
        Full(
          attributeDocumentation
            .BankId(bankId.value)
            .Name(name)
            .Category(category.toString)
            .`TypeOfValue`(`type`.toString)
            .Description(description)
            .Alias(alias)
            .IsActive(isActive)
            .saveMe()
        )
      case Empty =>
        Full(
          AttributeDocumentation.create
            .BankId(bankId.value)
            .Name(name)
            .Category(category.toString)
            .`TypeOfValue`(`type`.toString)
            .Description(description)
            .Alias(alias)
            .IsActive(isActive)
            .saveMe()
        )
      case someError => someError
    }
    
  }

  def deleteAttributeDocumentation(attributeDocumentationId: String, 
                                   category: AttributeCategory.Value): Future[Box[Boolean]] = Future {
    AttributeDocumentation.find(
      By(AttributeDocumentation.AttributeDocumentationId, attributeDocumentationId),
      By(AttributeDocumentation.Category, category.toString)
    ) match {
      case Full(attribute) => Full(attribute.delete_!)
      case Empty           => Empty ?~! ErrorMessages.AttributeNotFound
      case unhandledError  => 
        logger.error(unhandledError)
        Full(false)
    }
  }
  
}

class AttributeDocumentation extends AttributeDocumentationTrait with LongKeyedMapper[AttributeDocumentation] with IdPK with CreatedUpdated {
  override def getSingleton = AttributeDocumentation
  object AttributeDocumentationId extends MappedUUID(this)
  object BankId extends MappedString(this, 50)
  object Name extends MappedString(this, 50)
  object Category extends MappedString(this, 50)
  object `TypeOfValue` extends MappedString(this, 50)
  object Description extends MappedString(this, 50)
  object Alias extends MappedString(this, 50)
  object IsActive extends MappedBoolean(this)

  import com.openbankproject.commons.model.{BankId => BankIdCommonModel}
  def attributeDocumentationId: String = AttributeDocumentationId.get
  def bankId: BankIdCommonModel = BankIdCommonModel(BankId.get)
  def name: String = Name.get
  def category: AttributeCategory.Value = AttributeCategory.withName(Category.get)
  def `type`: AttributeType.Value = AttributeType.withName(`TypeOfValue`.get)
  def description: String = Description.get
  def alias: String = Alias.get
  def isActive: Boolean = IsActive.get

}

object AttributeDocumentation extends AttributeDocumentation with LongKeyedMetaMapper[AttributeDocumentation] {
  override def dbIndexes: List[BaseIndex[AttributeDocumentation]] = UniqueIndex(BankId, Name, Category) :: super.dbIndexes
}
