package code.accountattribute

import code.api.attributedefinition.AttributeDefinition
import code.products.MappedProduct
import code.util.{AttributeQueryTrait, MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.{AccountAttributeType, AttributeCategory}
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankId, BankIdAccountId, ProductAttribute, ProductCode, ViewId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.immutable.List
import scala.concurrent.Future


object MappedAccountAttributeProvider extends AccountAttributeProvider {

  override def getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode): Future[Box[List[AccountAttribute]]] =
    Future {
      Box !!  MappedAccountAttribute.findAll(
          By(MappedAccountAttribute.mAccountId, accountId.value),
          By(MappedAccountAttribute.mCode, productCode.value)
        )
    }

  override def getAccountAttributesByAccount(bankId: BankId,
                                             accountId: AccountId): Future[Box[List[AccountAttribute]]] = {
    Future {
      Box !!  MappedAccountAttribute.findAll(
        By(MappedAccountAttribute.mBankIdId, bankId.value),
        By(MappedAccountAttribute.mAccountId, accountId.value)
      )
    }
  }
  override def getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId,
                                                            accountId: AccountId,
                                                            viewId: ViewId): Future[Box[List[AccountAttribute]]] = {
    Future {
      val attributeDefinitions = AttributeDefinition.findAll(
        By(AttributeDefinition.BankId, bankId.value),
        By(AttributeDefinition.Category, AttributeCategory.Account.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value)) // Filter by view_id
      val accountAttributes = MappedAccountAttribute.findAll(
        By(MappedAccountAttribute.mBankIdId, bankId.value),
        By(MappedAccountAttribute.mAccountId, accountId.value)
      )
      val filteredAccountAttributes = for {
        definition <- attributeDefinitions
        attribute <- accountAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield {
        attribute
      }
      Full(filteredAccountAttributes)
    }
  }
  override def getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId],
                                                             viewId: ViewId): Future[Box[List[AccountAttribute]]] = {
    Future {
      val attributeDefinitions = AttributeDefinition.findAll(
        ByList(AttributeDefinition.BankId, accounts.map(_.bankId.value)),
        By(AttributeDefinition.Category, AttributeCategory.Account.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value)) // Filter by view_id
      val accountAttributes = MappedAccountAttribute.findAll(
        ByList(MappedAccountAttribute.mAccountId,accounts.map(_.accountId.value))
      ).filter( item => 
        accounts.exists( acc => 
          (acc.bankId.value, acc.accountId.value) == (item.bankId.value, item.accountId.value)
        )
      )
      val filteredAccountAttributes = for {
        definition <- attributeDefinitions
        attribute <- accountAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield {
        attribute
      }
      Full(filteredAccountAttributes)
    }
  }

  override def getAccountAttributeById(accountAttributeId: String): Future[Box[AccountAttribute]] = Future {
    MappedAccountAttribute.find(By(MappedAccountAttribute.mAccountAttributeId, accountAttributeId))
  }

  override def createOrUpdateAccountAttribute(bankId: BankId, 
                                              accountId: AccountId,
                                              productCode: ProductCode,
                                              accountAttributeId: Option[String],
                                              name: String,
                                              attributeType: AccountAttributeType.Value,
                                              value: String): Future[Box[AccountAttribute]] =  {
    accountAttributeId match {
      case Some(id) => Future {
        MappedAccountAttribute.find(By(MappedAccountAttribute.mAccountAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute
                .mBankIdId(bankId.value)
                .mAccountId(accountId.value)
                .mCode(productCode.value)
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
          MappedAccountAttribute.create
            .mBankIdId(bankId.value)
            .mAccountId(accountId.value)
            .mCode(productCode.value)
            .mName(name)
            .mType(attributeType.toString())
            .mValue(value)
            .saveMe()
        }
      }
    }
  }
  override def createAccountAttributes(bankId: BankId, 
                                       accountId: AccountId,
                                       productCode: ProductCode,
                                       accountAttributes: List[ProductAttribute]): Future[Box[List[AccountAttribute]]] = {
    Future {
      tryo {
        for {
          accountAttribute <- accountAttributes
        } yield {
          MappedAccountAttribute.create.mAccountId(accountId.value)
            .mBankIdId(bankId.value)
            .mCode(productCode.value)
            .mName(accountAttribute.name)
            .mType(accountAttribute.attributeType.toString())
            .mValue(accountAttribute.value)
            .saveMe()
        }
      }
    }
  }
  
  override def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      MappedAccountAttribute.bulkDelete_!!(By(MappedAccountAttribute.mAccountAttributeId, accountAttributeId))
    )
  }

  override def getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] = Future {
    Box !! {
      if (params.isEmpty) {
        MappedAccountAttribute.findAll(By(MappedAccountAttribute.mBankIdId, bankId.value)).map(_.accountId.value)
      } else {
        val paramList = params.toList
        val parameters: List[String] = MappedAccountAttribute.getParameters(paramList)
        val sqlParametersFilter = MappedAccountAttribute.getSqlParametersFilter(paramList)
        val accountIdList = paramList.isEmpty match {
          case true =>
            MappedAccountAttribute.findAll(
              By(MappedAccountAttribute.mBankIdId, bankId.value)
            ).map(_.accountId.value)
          case false =>
            MappedAccountAttribute.findAll(
              By(MappedAccountAttribute.mBankIdId, bankId.value),
              BySql(sqlParametersFilter, IHaveValidatedThisSQL("developer","2020-06-28"), parameters:_*)
            ).map(_.accountId.value)
        }
        accountIdList
      }
    }
  }
}

class MappedAccountAttribute extends AccountAttribute with LongKeyedMapper[MappedAccountAttribute] with IdPK {

  override def getSingleton = MappedAccountAttribute

  object mBankIdId extends UUIDString(this) // combination of this
  object mAccountId extends UUIDString(this) // combination of this

  object mCode extends MappedString(this, 50) // and this is unique
  object mAccountAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)


  override def bankId: BankId = BankId(mBankIdId.get)
  
  override def accountId: AccountId = AccountId(mAccountId.get)

  override def productCode: ProductCode = ProductCode(mCode.get)

  override def accountAttributeId: String = mAccountAttributeId.get

  override def name: String = mName.get

  override def attributeType: AccountAttributeType.Value = AccountAttributeType.withName(mType.get)

  override def value: String = mValue.get


}

//
object MappedAccountAttribute extends MappedAccountAttribute with LongKeyedMetaMapper[MappedAccountAttribute] with AttributeQueryTrait {
  override def dbIndexes: List[BaseIndex[MappedAccountAttribute]] = Index(mAccountId) :: Index(mAccountAttributeId) :: super.dbIndexes

  override val mParentId: BaseMappedField = mAccountId
  override val mBankId: BaseMappedField = mBankIdId
}

