package code.transactionRequestAttribute

import code.api.attributedefinition.AttributeDefinition
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionRequestAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionRequestAttribute, TransactionRequestId, ViewId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{By, BySql, IHaveValidatedThisSQL}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedTransactionRequestAttributeProvider extends TransactionRequestAttributeProvider {

  override def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttribute]]] =
    Future {
      Box !! MappedTransactionRequestAttribute.findAll(
        By(MappedTransactionRequestAttribute.mTransactionRequestId, transactionRequestId.value)
      )
    }

  override def getTransactionRequestAttributes(
                                                bankId: BankId,
                                                transactionRequestId: TransactionRequestId
                                              ): Future[Box[List[TransactionRequestAttribute]]] = {
    Future {
      Box !! MappedTransactionRequestAttribute.findAll(
        By(MappedTransactionRequestAttribute.mBankId, bankId.value),
        By(MappedTransactionRequestAttribute.mTransactionRequestId, transactionRequestId.value)
      )
    }
  }

  override def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                              transactionRequestId: TransactionRequestId,
                                                              viewId: ViewId): Future[Box[List[TransactionRequestAttribute]]] = {
    Future {
      val attributeDefinitions = AttributeDefinition.findAll(
        By(AttributeDefinition.BankId, bankId.value),
        By(AttributeDefinition.Category, AttributeCategory.Account.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value)) // Filter by view_id
      val transactionRequestAttributes = MappedTransactionRequestAttribute.findAll(
        By(MappedTransactionRequestAttribute.mBankId, bankId.value),
        By(MappedTransactionRequestAttribute.mTransactionRequestId, transactionRequestId.value)
      )
      val filteredTransactionRequestAttributes = for {
        definition <- attributeDefinitions
        attribute <- transactionRequestAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield {
        attribute
      }
      Full(filteredTransactionRequestAttributes)
    }
  }

  override def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttribute]] = Future {
    MappedTransactionRequestAttribute.find(By(MappedTransactionRequestAttribute.mTransactionRequestAttributeId, transactionRequestAttributeId))
  }

  override def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] =
    Future {
      Box !! {
        if (params.isEmpty) {
          MappedTransactionRequestAttribute.findAll(By(MappedTransactionRequestAttribute.mBankId, bankId.value)).map(_.transactionRequestId.value)
        } else {
          val paramList = params.toList
          val parameters: List[String] = MappedTransactionRequestAttribute.getParameters(paramList)
          val sqlParametersFilter = MappedTransactionRequestAttribute.getSqlParametersFilter(paramList)
          val transactionRequestIdList = paramList.isEmpty match {
            case true =>
              MappedTransactionRequestAttribute.findAll(
                By(MappedTransactionRequestAttribute.mBankId, bankId.value)
              ).map(_.transactionRequestId.value)
            case false =>
              MappedTransactionRequestAttribute.findAll(
                By(MappedTransactionRequestAttribute.mBankId, bankId.value),
                BySql(sqlParametersFilter, IHaveValidatedThisSQL("developer", "2020-06-28"), parameters: _*)
              ).map(_.transactionRequestId.value)
          }
          transactionRequestIdList
        }
      }
    }

  override def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                         transactionRequestId: TransactionRequestId,
                                                         transactionRequestAttributeId: Option[String],
                                                         name: String,
                                                         attributeType: TransactionRequestAttributeType.Value,
                                                         value: String): Future[Box[TransactionRequestAttribute]] = {
    transactionRequestAttributeId match {
      case Some(id) => Future {
        MappedTransactionRequestAttribute.find(By(MappedTransactionRequestAttribute.mTransactionRequestAttributeId, id)) match {
          case Full(attribute) => tryo {
            attribute
              .mBankId(bankId.value)
              .mTransactionRequestId(transactionRequestId.value)
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
          MappedTransactionRequestAttribute.create
            .mBankId(bankId.value)
            .mTransactionRequestId(transactionRequestId.value)
            .mName(name)
            .mType(attributeType.toString())
            .mValue(value)
            .saveMe()
        }
      }
    }
  }

  override def createTransactionRequestAttributes(bankId: BankId,
                                                  transactionRequestId: TransactionRequestId,
                                                  transactionRequestAttributes: List[TransactionRequestAttribute]): Future[Box[List[TransactionRequestAttribute]]] = {
    Future {
      tryo {
        for {
          transactionRequestAttribute <- transactionRequestAttributes
        } yield {
          MappedTransactionRequestAttribute.create.mTransactionRequestId(transactionRequestId.value)
            .mBankId(bankId.value)
            .mName(transactionRequestAttribute.name)
            .mType(transactionRequestAttribute.attributeType.toString())
            .mValue(transactionRequestAttribute.value)
            .saveMe()
        }
      }
    }
  }

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      MappedTransactionRequestAttribute.bulkDelete_!!(By(MappedTransactionRequestAttribute.mTransactionRequestAttributeId, transactionRequestAttributeId))
    )
  }
}