package code.transactionRequestAttribute

import code.api.attributedefinition.AttributeDefinition
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionRequestAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionRequestAttributeJsonV400, TransactionRequestAttributeTrait, TransactionRequestId, ViewId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{By, BySql,In, IHaveValidatedThisSQL}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedTransactionRequestAttributeProvider extends TransactionRequestAttributeProvider {

  override def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      Box !! TransactionRequestAttribute.findAll(
        By(TransactionRequestAttribute.TransactionRequestId, transactionRequestId.value)
      )
    }

  override def getTransactionRequestAttributes(
                                                bankId: BankId,
                                                transactionRequestId: TransactionRequestId
                                              ): Future[Box[List[TransactionRequestAttributeTrait]]] = {
    Future {
      Box !! TransactionRequestAttribute.findAll(
        By(TransactionRequestAttribute.BankId, bankId.value),
        By(TransactionRequestAttribute.TransactionRequestId, transactionRequestId.value)
      )
    }
  }

  override def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                              transactionRequestId: TransactionRequestId,
                                                              viewId: ViewId): Future[Box[List[TransactionRequestAttributeTrait]]] = {
    Future {
      val attributeDefinitions = AttributeDefinition.findAll(
        By(AttributeDefinition.BankId, bankId.value),
        By(AttributeDefinition.Category, AttributeCategory.Account.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value)) // Filter by view_id
      val transactionRequestAttributes = TransactionRequestAttribute.findAll(
        By(TransactionRequestAttribute.BankId, bankId.value),
        By(TransactionRequestAttribute.TransactionRequestId, transactionRequestId.value)
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
    TransactionRequestAttribute.find(By(TransactionRequestAttribute.TransactionRequestAttributeId, transactionRequestAttributeId))
  }

  override def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[String]]] =
    getByAttributeNameValues(bankId: BankId, params, isPersonal)
      .map(
        attributesBox =>attributesBox
          .map(attributes=>
            attributes.map(attribute =>
              attribute.transactionRequestId.value
          )))

  override def getByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      Box !! {
        if (params.isEmpty) {
          TransactionRequestAttribute.findAll(
            By(TransactionRequestAttribute.BankId, bankId.value),
            By(TransactionRequestAttribute.IsPersonal, true)
          )
        } else {
          val paramList = params.toList
          val parameters: List[String] = TransactionRequestAttribute.getParameters(paramList)
          val sqlParametersFilter = TransactionRequestAttribute.getSqlParametersFilter(paramList)
          paramList.isEmpty match {
            case true =>
              TransactionRequestAttribute.findAll(
                By(TransactionRequestAttribute.BankId, bankId.value),
                By(TransactionRequestAttribute.IsPersonal, true)
              )
            case false =>
              TransactionRequestAttribute.findAll(
                By(TransactionRequestAttribute.BankId, bankId.value),
                By(TransactionRequestAttribute.IsPersonal, true),
                BySql(sqlParametersFilter, IHaveValidatedThisSQL("developer", "2020-06-28"), parameters: _*)
              )
          }
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
        TransactionRequestAttribute.find(By(TransactionRequestAttribute.TransactionRequestAttributeId, id)) match {
          case Full(attribute) => tryo {
            attribute
              .BankId(bankId.value)
              .TransactionRequestId(transactionRequestId.value)
              .Name(name)
              .Type(attributeType.toString)
              .`Value`(value)
              .saveMe()
          }
          case _ => Empty
        }
      }
      case None => Future {
        Full {
          TransactionRequestAttribute.create
            .BankId(bankId.value)
            .TransactionRequestId(transactionRequestId.value)
            .Name(name)
            .Type(attributeType.toString())
            .`Value`(value)
            .saveMe()
        }
      }
    }
  }

  override def createTransactionRequestAttributes(
    bankId: BankId,
    transactionRequestId: TransactionRequestId,
    transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
    isPersonal: Boolean
  ): Future[Box[List[TransactionRequestAttributeTrait]]] = {
    Future {
      tryo {
        for {
          transactionRequestAttribute <- transactionRequestAttributes
        } yield {
          TransactionRequestAttribute.create.TransactionRequestId(transactionRequestId.value)
            .BankId(bankId.value)
            .Name(transactionRequestAttribute.name)
            .Type(transactionRequestAttribute.attribute_type)
            .`Value`(transactionRequestAttribute.value)
            .IsPersonal(isPersonal)
            .saveMe()
        }
      }
    }
  }

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      TransactionRequestAttribute.bulkDelete_!!(By(TransactionRequestAttribute.TransactionRequestAttributeId, transactionRequestAttributeId))
    )
  }
}