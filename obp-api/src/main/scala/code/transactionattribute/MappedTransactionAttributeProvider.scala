package code.transactionattribute

import code.api.attributedefinition.AttributeDefinition
import code.util.{AttributeQueryTrait, MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionAttribute, TransactionId, ViewId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object MappedTransactionAttributeProvider extends TransactionAttributeProvider {

  override def getTransactionAttributesFromProvider(transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] =
    Future {
      Box !!  MappedTransactionAttribute.findAll(
          By(MappedTransactionAttribute.mTransactionId, transactionId.value)
        )
    }

  override def getTransactionAttributes(
    bankId: BankId,
    transactionId: TransactionId
  ): Future[Box[List[TransactionAttribute]]] = {
    Future {
      Box !!  MappedTransactionAttribute.findAll(
        By(MappedTransactionAttribute.mBankId, bankId.value),
        By(MappedTransactionAttribute.mTransactionId, transactionId.value)
      )
    }
  }
  override def getTransactionAttributesCanBeSeenOnView(bankId: BankId,
                                                       transactionId: TransactionId, 
                                                       viewId: ViewId): Future[Box[List[TransactionAttribute]]] = {
    Future {
      val attributeDefinitions = AttributeDefinition.findAll(
        By(AttributeDefinition.BankId, bankId.value),
        By(AttributeDefinition.Category, AttributeCategory.Transaction.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value)) // Filter by view_id
      val transactionAttributes = MappedTransactionAttribute.findAll(
        By(MappedTransactionAttribute.mBankId, bankId.value),
        By(MappedTransactionAttribute.mTransactionId, transactionId.value)
      )
      val filteredTransactionAttributes = for {
        definition <- attributeDefinitions
        attribute <- transactionAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield {
        attribute
      }
      Full(filteredTransactionAttributes)
    }
  }

  override def getTransactionsAttributesCanBeSeenOnView(bankId: BankId,
                                                        transactionIds: List[TransactionId],
                                                        viewId: ViewId): Future[Box[List[TransactionAttribute]]] = {
    Future {
      val attributeDefinitions = AttributeDefinition.findAll(
        By(AttributeDefinition.BankId, bankId.value),
        By(AttributeDefinition.Category, AttributeCategory.Transaction.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value)) // Filter by view_id
      val transactionsAttributes = MappedTransactionAttribute.findAll(
        ByList(MappedTransactionAttribute.mTransactionId, transactionIds.map(_.value))
      ).filter( item =>
        transactionIds.exists( acc =>
          (bankId.value, acc.value) == (item.bankId.value, item.transactionId.value)
        )
      )
      val filteredTransactionAttributes = for {
        definition <- attributeDefinitions
        attribute <- transactionsAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield {
        attribute
      }
      Full(filteredTransactionAttributes)
    }
  }

  override def getTransactionAttributeById(transactionAttributeId: String): Future[Box[TransactionAttribute]] = Future {
    MappedTransactionAttribute.find(By(MappedTransactionAttribute.mTransactionAttributeId, transactionAttributeId))
  }

  override def getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]  =
    Future {
      Box !! {
        if (params.isEmpty) {
          MappedTransactionAttribute.findAll(By(MappedTransactionAttribute.mBankId, bankId.value)).map(_.transactionId.value)
        } else {
          val paramList = params.toList
          val parameters: List[String] = MappedTransactionAttribute.getParameters(paramList)
          val sqlParametersFilter = MappedTransactionAttribute.getSqlParametersFilter(paramList)
          val transactionIdList = paramList.isEmpty match {
            case true =>
              MappedTransactionAttribute.findAll(
                By(MappedTransactionAttribute.mBankId, bankId.value)
              ).map(_.transactionId.value)
            case false =>
              MappedTransactionAttribute.findAll(
                By(MappedTransactionAttribute.mBankId, bankId.value),
                BySql(sqlParametersFilter, IHaveValidatedThisSQL("developer","2020-06-28"), parameters:_*)
              ).map(_.transactionId.value)
          }
          transactionIdList
        }
      }
    }
  
  override def createOrUpdateTransactionAttribute(bankId: BankId, 
                                              transactionId: TransactionId,
                                              transactionAttributeId: Option[String],
                                              name: String,
                                              attributeType: TransactionAttributeType.Value,
                                              value: String): Future[Box[TransactionAttribute]] =  {
    transactionAttributeId match {
      case Some(id) => Future {
        MappedTransactionAttribute.find(By(MappedTransactionAttribute.mTransactionAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute
                .mBankId(bankId.value)
                .mTransactionId(transactionId.value)
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
          MappedTransactionAttribute.create
            .mBankId(bankId.value)
            .mTransactionId(transactionId.value)
            .mName(name)
            .mType(attributeType.toString())
            .mValue(value)
            .saveMe()
        }
      }
    }
  }
  override def createTransactionAttributes(bankId: BankId, 
                                       transactionId: TransactionId,
                                       transactionAttributes: List[TransactionAttribute]): Future[Box[List[TransactionAttribute]]] = {
    Future {
      tryo {
        for {
          transactionAttribute <- transactionAttributes
        } yield {
          MappedTransactionAttribute.create.mTransactionId(transactionId.value)
            .mBankId(bankId.value)
            .mName(transactionAttribute.name)
            .mType(transactionAttribute.attributeType.toString())
            .mValue(transactionAttribute.value)
            .saveMe()
        }
      }
    }
  }
  
  override def deleteTransactionAttribute(transactionAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      MappedTransactionAttribute.bulkDelete_!!(By(MappedTransactionAttribute.mTransactionAttributeId, transactionAttributeId))
    )
  }
}

class MappedTransactionAttribute extends TransactionAttribute with LongKeyedMapper[MappedTransactionAttribute] with IdPK {

  override def getSingleton = MappedTransactionAttribute

  object mBankId extends UUIDString(this) // combination of this
 
  object mTransactionId extends UUIDString(this) // combination of this

  object mTransactionAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)


  override def bankId: BankId = BankId(mBankId.get)
  
  override def transactionId: TransactionId = TransactionId(mTransactionId.get)

  override def transactionAttributeId: String = mTransactionAttributeId.get

  override def name: String = mName.get

  override def attributeType: TransactionAttributeType.Value = TransactionAttributeType.withName(mType.get)

  override def value: String = mValue.get


}

object MappedTransactionAttribute extends MappedTransactionAttribute with LongKeyedMetaMapper[MappedTransactionAttribute]
  with AttributeQueryTrait {
  override def dbIndexes: List[BaseIndex[MappedTransactionAttribute]] = Index(mTransactionId) :: Index(mTransactionAttributeId) :: super.dbIndexes
  override val mParentId: BaseMappedField = mTransactionId
}

