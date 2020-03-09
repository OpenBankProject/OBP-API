package code.productAttributeattribute

import code.productattribute.ProductAttributeProvider
import code.util.{AttributeQueryTrait, MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductCode}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{BaseMappedField, _}
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future


object MappedProductAttributeProvider extends ProductAttributeProvider {

  override def getProductAttributesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductAttribute]]] =
    Future {
      Box !!  MappedProductAttribute.findAll(
          By(MappedProductAttribute.mBankId, bankId.value),
          By(MappedProductAttribute.mCode, productCode.value)
        )
    }

  override def getProductAttributeById(productAttributeId: String): Future[Box[ProductAttribute]] = Future {
     MappedProductAttribute.find(By(MappedProductAttribute.mProductAttributeId, productAttributeId))
  }

  override def createOrUpdateProductAttribute(bankId: BankId,
                                              productCode: ProductCode,
                                              productAttributeId: Option[String],
                                              name: String,
                                              attributType: ProductAttributeType.Value,
                                              value: String): Future[Box[ProductAttribute]] =  {
     productAttributeId match {
      case Some(id) => Future {
         MappedProductAttribute.find(By(MappedProductAttribute.mProductAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute.mBankId(bankId.value)
                .mCode(productCode.value)
                .mName(name)
                .mType(attributType.toString)
                .mValue(value)
                .saveMe()
            }
            case _ => Empty
          }
      }
      case None => Future {
        Full {
          MappedProductAttribute.create
            .mBankId(bankId.value)
            .mCode(productCode.value)
            .mName(name)
            .mType(attributType.toString())
            .mValue(value)
            .saveMe()
        }
      }
    }
  }

  override def deleteProductAttribute(productAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      MappedProductAttribute.bulkDelete_!!(By(MappedProductAttribute.mProductAttributeId, productAttributeId))
    )
  }
}

class MappedProductAttribute extends ProductAttribute with LongKeyedMapper[MappedProductAttribute] with IdPK {

  override def getSingleton = MappedProductAttribute

  object mBankId extends UUIDString(this) // combination of this

  object mCode extends MappedString(this, 50) // and this is unique
  object mProductAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)


  override def bankId: BankId = BankId(mBankId.get)

  override def productCode: ProductCode = ProductCode(mCode.get)

  override def productAttributeId: String = mProductAttributeId.get

  override def name: String = mName.get

  override def attributeType: ProductAttributeType.Value = ProductAttributeType.withName(mType.get)

  override def value: String = mValue.get


}

//
object MappedProductAttribute extends MappedProductAttribute with LongKeyedMetaMapper[MappedProductAttribute] with AttributeQueryTrait {
  override def dbIndexes = Index(mBankId) :: Index(mProductAttributeId) :: super.dbIndexes

  /**
   * Attribute entity's parent id, for example: CustomerAttribute.customerId,
   * need implemented in companion object
   */
  override val mParentId: BaseMappedField = mCode
}

