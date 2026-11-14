package code.accountattribute

import code.api.Constant.{PARAM_LOCALE, PARAM_TIMESTAMP}
import code.api.attributedefinition.AttributeDefinition
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.{AccountAttributeType, AttributeCategory}
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankId, BankIdAccountId, ProductAttribute, ProductCode, ViewId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.Fragments
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One account-attribute row, standing in for the Lift entity in return types. */
case class AccountAttributeRow(
  bankId: BankId,
  accountId: AccountId,
  productCode: ProductCode,
  accountAttributeId: String,
  attributeType: AccountAttributeType.Value,
  name: String,
  value: String,
  productInstanceCode: Option[String]
) extends AccountAttribute

/**
 * Doobie implementation of the account-attribute store, replacing the Lift MappedAccountAttribute
 * entity.
 *
 * There is no unique index on this table: only plain indexes on mAccountId and
 * mAccountAttributeId. createOrUpdateAccountAttribute finds by accountAttributeId to decide
 * update vs create, matching the Mapper version, but nothing in the schema stops two rows sharing
 * an id.
 *
 * getAccountAttributesByAccountCanBeSeenOnView / getAccountAttributesByAccountsCanBeSeenOnView
 * still read AttributeDefinition (a separate, not-yet-migrated Mapper entity) directly and join
 * in plain Scala, exactly as the Mapper version did - only the AccountAttribute-table read moved
 * to Doobie.
 *
 * getAccountIdsByParams reproduces the Mapper version's BySql(sqlParametersFilter, ...) row-level
 * filter: OR-across-attributes semantics (an account matches if ANY requested name/value pair is
 * present on one of its attribute rows), not an AND-across-all-requested-names filter.
 */
object DoobieAccountAttributeProvider extends AccountAttributeProvider {

  // Only `id` is NOT NULL on this table. mproductinstancecode in particular was added to the model
  // long after the table existed, and Schemifier added it with no backfill, so every row written
  // before that release holds SQL NULL there. Binding bare made doobie raise NonNullableColumnRead
  // and fail the whole listing; each column is collapsed the way its MappedString read a NULL.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String])

  private def rowOf(r: Row): AccountAttributeRow =
    AccountAttributeRow(
      bankId = BankId(r._1.orNull),
      accountId = AccountId(r._2.orNull),
      productCode = ProductCode(r._3.orNull),
      accountAttributeId = r._4.orNull,
      attributeType = AccountAttributeType.withName(r._5.orNull),
      name = r._6.orNull,
      value = r._7.orNull,
      // Already an Option field: a NULL column is None, not Some(null) as the bare bind produced.
      productInstanceCode = r._8
    )

  private val selectCols: Fragment =
    fr"""SELECT mbankidid, maccountid, mcode, maccountattributeid, mtype, mname, mvalue, mproductinstancecode
         FROM mappedaccountattribute"""

  override def getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode): Future[Box[List[AccountAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE maccountid = ${accountId.value} AND mcode = ${productCode.value}")
          .query[Row].to[List]
      ).map(rowOf)
    }

  override def getAccountAttributesByAccount(bankId: BankId, accountId: AccountId): Future[Box[List[AccountAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankidid = ${bankId.value} AND maccountid = ${accountId.value}")
          .query[Row].to[List]
      ).map(rowOf)
    }

  override def getAccountAttributesByAccountCanBeSeenOnView(
    bankId: BankId,
    accountId: AccountId,
    viewId: ViewId
  ): Future[Box[List[AccountAttribute]]] = Future {
    val attributeDefinitions = AttributeDefinition
      .findAllByBankIdAndCategory(bankId.value, AttributeCategory.Account.toString)
      .filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val accountAttributes = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankidid = ${bankId.value} AND maccountid = ${accountId.value}")
        .query[Row].to[List]
    ).map(rowOf)
    val filteredAccountAttributes = for {
      definition <- attributeDefinitions
      attribute <- accountAttributes
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filteredAccountAttributes)
  }

  override def getAccountAttributesByAccountsCanBeSeenOnView(
    accounts: List[BankIdAccountId],
    viewId: ViewId
  ): Future[Box[List[AccountAttribute]]] = Future {
    if (accounts.isEmpty) {
      Full(Nil)
    } else {
      val attributeDefinitions = AttributeDefinition
        .findAllByBankIdsAndCategory(accounts.map(_.bankId.value), AttributeCategory.Account.toString)
        .filter(_.canBeSeenOnViews.exists(_ == viewId.value))
      val accountIds = accounts.map(_.accountId.value).distinct
      val inFrag = Fragments.in(fr"maccountid", cats.data.NonEmptyList.fromListUnsafe(accountIds))
      val accountAttributes = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE " ++ inFrag)
          .query[Row].to[List]
      ).map(rowOf).filter { item =>
        accounts.exists(acc => (acc.bankId.value, acc.accountId.value) == (item.bankId.value, item.accountId.value))
      }
      val filteredAccountAttributes = for {
        definition <- attributeDefinitions
        attribute <- accountAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield attribute
      Full(filteredAccountAttributes)
    }
  }

  override def getAccountAttributeById(accountAttributeId: String): Future[Box[AccountAttribute]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE maccountattributeid = $accountAttributeId LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateAccountAttribute(
    bankId: BankId,
    accountId: AccountId,
    productCode: ProductCode,
    accountAttributeId: Option[String],
    name: String,
    attributeType: AccountAttributeType.Value,
    value: String,
    productInstanceCode: Option[String]
  ): Future[Box[AccountAttribute]] = {
    val productInstanceCodeValue = productInstanceCode.getOrElse("")
    accountAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE maccountattributeid = $id LIMIT 1")
            .query[Row].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE mappedaccountattribute
                      SET mbankidid = ${bankId.value}, maccountid = ${accountId.value}, mcode = ${productCode.value},
                          mname = $name, mtype = ${attributeType.toString}, mvalue = $value, mproductinstancecode = $productInstanceCodeValue
                      WHERE maccountattributeid = $id"""
                  .update.run)
              AccountAttributeRow(bankId, accountId, productCode, id, attributeType, name, value, Some(productInstanceCodeValue))
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedaccountattribute
                    (mbankidid, maccountid, mcode, maccountattributeid, mname, mtype, mvalue, mproductinstancecode)
                  VALUES (${bankId.value}, ${accountId.value}, ${productCode.value}, $id, $name, ${attributeType.toString}, $value, $productInstanceCodeValue)"""
              .update.run)
          AccountAttributeRow(bankId, accountId, productCode, id, attributeType, name, value, Some(productInstanceCodeValue))
        }
      }
    }
  }

  override def createAccountAttributes(
    bankId: BankId,
    accountId: AccountId,
    productCode: ProductCode,
    accountAttributes: List[ProductAttribute],
    productInstanceCode: Option[String]
  ): Future[Box[List[AccountAttribute]]] = {
    val productInstanceCodeValue = productInstanceCode.getOrElse("")
    Future {
      tryo {
        accountAttributes.map { accountAttribute =>
          val id = APIUtil.generateUUID()
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedaccountattribute
                    (mbankidid, maccountid, mcode, maccountattributeid, mname, mtype, mvalue, mproductinstancecode)
                  VALUES (${bankId.value}, ${accountId.value}, ${productCode.value}, $id, ${accountAttribute.name}, ${accountAttribute.attributeType.toString}, ${accountAttribute.value}, $productInstanceCodeValue)"""
              .update.run)
          AccountAttributeRow(
            bankId, accountId, productCode, id,
            AccountAttributeType.withName(accountAttribute.attributeType.toString),
            accountAttribute.name, accountAttribute.value, Some(productInstanceCodeValue))
        }
      }
    }
  }

  override def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedaccountattribute WHERE maccountattributeid = $accountAttributeId".update.run) >= 0
    )
  }

  override def getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] = Future {
    val paramFiltered = params.filterNot(_._1 == PARAM_TIMESTAMP).filterNot(_._1 == PARAM_LOCALE)

    Full {
      if (paramFiltered.isEmpty) {
        DoobieUtil.runQuery(
          sql"SELECT maccountid FROM mappedaccountattribute WHERE mbankidid = ${bankId.value}".query[String].to[List])
      } else {
        val paramList = paramFiltered.toList
        val filterFrag: Fragment = paramList.map { case (name, values) =>
          if (values.size == 1) {
            fr"(mname = $name AND mvalue = ${values.head})"
          } else {
            val valueFragments = values.map(v => fr"$v")
            val inClause = valueFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
            fr"(mname = $name AND mvalue IN (" ++ inClause ++ fr"))"
          }
        }.reduceOption((a, b) => a ++ fr" OR " ++ b).getOrElse(fr"1=1")

        DoobieUtil.runQuery(
          (fr"SELECT maccountid FROM mappedaccountattribute WHERE mbankidid = ${bankId.value} AND (" ++ filterFrag ++ fr")")
            .query[String].to[List])
      }
    }
  }

  /** Direct query used by deletion.DeleteBankCascade.delete. */
  def getAccountAttributesByBankSync(bankId: String): List[AccountAttributeRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankidid = $bankId")
        .query[Row].to[List]
    ).map(rowOf)

  /** Direct query used by deletion.DeleteAccountCascade.delete. */
  def deleteAccountAttributesByBankAndAccount(bankId: String, accountId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedaccountattribute WHERE mbankidid = $bankId AND maccountid = $accountId".update.run)
    true
  }
}
