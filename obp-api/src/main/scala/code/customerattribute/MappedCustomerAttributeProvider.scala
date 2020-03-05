package code.customerattribute

import code.util.{AttributeQueryTrait, MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, Customer, CustomerAttribute, CustomerId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object MappedCustomerAttributeProvider extends CustomerAttributeProvider {

  override def getCustomerAttributesFromProvider(customerId: CustomerId): Future[Box[List[CustomerAttribute]]] =
    Future {
      Box !!  MappedCustomerAttribute.findAll(
          By(MappedCustomerAttribute.mCustomerId, customerId.value)
        )
    }

  override def getCustomerAttributes(bankId: BankId,
                                             customerId: CustomerId): Future[Box[List[CustomerAttribute]]] = {
    Future {
      Box !!  MappedCustomerAttribute.findAll(
        By(MappedCustomerAttribute.mBankIdId, bankId.value),
        By(MappedCustomerAttribute.mCustomerId, customerId.value)
      )
    }
  }

  override def getCustomerIdByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] =
  Future {
    Box !! {MappedCustomerAttribute.getParentIdByParams(bankId, params)}
  }

  def getCustomerAttributesForCustomers(customers: List[Customer]): Future[Box[List[(Customer, List[CustomerAttribute])]]] = {
    Future {
      Box !! customers.map( customer =>
        (
          customer,
          MappedCustomerAttribute.findAll(
            By(MappedCustomerAttribute.mBankIdId, customer.bankId),
            By(MappedCustomerAttribute.mCustomerId, customer.customerId)
          )
        )
      )
    }
  }

  override def getCustomerAttributeById(customerAttributeId: String): Future[Box[CustomerAttribute]] = Future {
    MappedCustomerAttribute.find(By(MappedCustomerAttribute.mCustomerAttributeId, customerAttributeId))
  }

  override def createOrUpdateCustomerAttribute(bankId: BankId, 
                                              customerId: CustomerId,
                                              customerAttributeId: Option[String],
                                              name: String,
                                              attributeType: CustomerAttributeType.Value,
                                              value: String): Future[Box[CustomerAttribute]] =  {
    customerAttributeId match {
      case Some(id) => Future {
        MappedCustomerAttribute.find(By(MappedCustomerAttribute.mCustomerAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute
                .mBankIdId(bankId.value)
                .mCustomerId(customerId.value)
                .mName(name)
                .mType(attributeType.toString)
                .mValue(value)
                .saveMe()
            }
            case _ => Empty
          }
      }
      case None => Future {
        Full {
          MappedCustomerAttribute.create
            .mBankIdId(bankId.value)
            .mCustomerId(customerId.value)
            .mName(name)
            .mType(attributeType.toString())
            .mValue(value)
            .saveMe()
        }
      }
    }
  }
  override def createCustomerAttributes(bankId: BankId, 
                                       customerId: CustomerId,
                                       customerAttributes: List[CustomerAttribute]): Future[Box[List[CustomerAttribute]]] = {
    Future {
      tryo {
        for {
          customerAttribute <- customerAttributes
        } yield {
          MappedCustomerAttribute.create.mCustomerId(customerId.value)
            .mBankIdId(bankId.value)
            .mName(customerAttribute.name)
            .mType(customerAttribute.attributeType.toString())
            .mValue(customerAttribute.value)
            .saveMe()
        }
      }
    }
  }
  
  override def deleteCustomerAttribute(customerAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      MappedCustomerAttribute.bulkDelete_!!(By(MappedCustomerAttribute.mCustomerAttributeId, customerAttributeId))
    )
  }
}

class MappedCustomerAttribute extends CustomerAttribute with LongKeyedMapper[MappedCustomerAttribute] with IdPK {

  override def getSingleton = MappedCustomerAttribute

  object mBankIdId extends UUIDString(this) // combination of this

  object mCustomerId extends UUIDString(this) // combination of this

  object mCustomerAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)


  override def bankId: BankId = BankId(mBankIdId.get)

  override def customerId: CustomerId = CustomerId(mCustomerId.get)

  override def customerAttributeId: String = mCustomerAttributeId.get

  override def name: String = mName.get

  override def attributeType: CustomerAttributeType.Value = CustomerAttributeType.withName(mType.get)

  override def value: String = mValue.get


}

//
object MappedCustomerAttribute extends MappedCustomerAttribute
  with LongKeyedMetaMapper[MappedCustomerAttribute]
  with AttributeQueryTrait {
  override def dbIndexes: List[BaseIndex[MappedCustomerAttribute]] = Index(mCustomerId) :: Index(mCustomerAttributeId) :: super.dbIndexes

  override val mParentId: BaseMappedField = mCustomerId
}

