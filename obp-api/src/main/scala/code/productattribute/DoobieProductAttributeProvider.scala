package code.productattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductCode}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One product-attribute row, standing in for the Lift entity in return types. */
case class ProductAttributeRow(
  bankId: BankId,
  productCode: ProductCode,
  productAttributeId: String,
  attributeType: ProductAttributeType.Value,
  name: String,
  value: String,
  isActive: Option[Boolean]
) extends ProductAttribute

/**
 * Doobie implementation of the product-attribute store, replacing the Lift MappedProductAttribute
 * entity.
 *
 * There is no unique index on this table: only plain indexes on mBankId and
 * mProductAttributeId. createOrUpdateProductAttribute finds by productAttributeId to decide
 * update vs create, matching the Mapper version, but nothing in the schema stops two rows sharing
 * an id.
 *
 * Unlike AtmAttribute/BankAttribute/CounterpartyAttribute/RegulatedEntityAttribute, the Type
 * column here is stored as mtype (not type_c) - it does not collide with H2's reserved TYPE
 * keyword.
 */
object DoobieProductAttributeProvider extends ProductAttributeProvider {

  private def rowOf(r: (String, String, String, String, String, String, Option[Boolean])): ProductAttributeRow =
    ProductAttributeRow(
      bankId = BankId(r._1),
      productCode = ProductCode(r._2),
      productAttributeId = r._3,
      attributeType = ProductAttributeType.withName(r._4),
      name = r._5,
      value = r._6,
      isActive = r._7
    )

  private val selectCols: Fragment =
    fr"SELECT mbankid, mcode, mproductattributeid, mtype, mname, mvalue, isactive FROM mappedproductattribute"

  override def getProductAttributesFromProvider(bank: BankId, productCode: ProductCode): Future[Box[List[ProductAttribute]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${bank.value} AND mcode = ${productCode.value}")
          .query[(String, String, String, String, String, String, Option[Boolean])].to[List]
      ).map(rowOf)
    }

  override def getProductAttributeById(productAttributeId: String): Future[Box[ProductAttribute]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mproductattributeid = $productAttributeId LIMIT 1")
        .query[(String, String, String, String, String, String, Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def createOrUpdateProductAttribute(
    bankId: BankId,
    productCode: ProductCode,
    productAttributeId: Option[String],
    name: String,
    attributeType: ProductAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[ProductAttribute]] = {
    val activeValue = isActive.getOrElse(true)
    productAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE mproductattributeid = $id LIMIT 1")
            .query[(String, String, String, String, String, String, Option[Boolean])].option
        ) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runUpdate(
                sql"""UPDATE mappedproductattribute
                      SET mbankid = ${bankId.value}, mcode = ${productCode.value}, mname = $name, mtype = ${attributeType.toString}, mvalue = $value, isactive = $activeValue
                      WHERE mproductattributeid = $id"""
                  .update.run)
              ProductAttributeRow(bankId, productCode, id, attributeType, name, value, Some(activeValue))
            }
          case None => Empty
        }
      }
      case None => Future {
        val id = APIUtil.generateUUID()
        Full {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappedproductattribute (mbankid, mcode, mproductattributeid, mname, mtype, mvalue, isactive)
                  VALUES (${bankId.value}, ${productCode.value}, $id, $name, ${attributeType.toString}, $value, $activeValue)"""
              .update.run)
          ProductAttributeRow(bankId, productCode, id, attributeType, name, value, Some(activeValue))
        }
      }
    }
  }

  override def deleteProductAttribute(productAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      DoobieUtil.runUpdate(
        sql"DELETE FROM mappedproductattribute WHERE mproductattributeid = $productAttributeId".update.run) >= 0
    )
  }

  /** Direct query used by MappedProductCollectionItemProvider.getProductCollectionItemsTree. */
  def getProductAttributesSync(bankId: String, productCode: String): List[ProductAttributeRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = $bankId AND mcode = $productCode")
        .query[(String, String, String, String, String, String, Option[Boolean])].to[List]
    ).map(rowOf)

  /** Direct query used by deletion.DeleteProductCascade.deleteProductAttributes. */
  def deleteProductAttributesByBankAndCode(bankId: String, productCode: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedproductattribute WHERE mbankid = $bankId AND mcode = $productCode".update.run)
    true
  }

  /** Direct query used by LocalMappedConnector.getProducts (no attribute-name filters). */
  def getProductCodesForBank(bankId: String): List[String] =
    DoobieUtil.runQuery(sql"SELECT mcode FROM mappedproductattribute WHERE mbankid = $bankId".query[String].to[List])

  /**
   * Direct query used by LocalMappedConnector.getProducts (with attribute-name filters).
   *
   * Returns the mcode of every attribute row matching ANY of the requested (name, value) pairs -
   * OR-across-attributes semantics, matching the Mapper version's BySql(sqlParametersFilter, ...)
   * row-level filter exactly (not an AND-across-all-requested-names filter).
   */
  def getProductCodesMatchingAnyAttribute(bankId: String, params: List[(String, List[String])]): List[String] = {
    val filterFrag: Fragment = params.map { case (name, values) =>
      if (values.size == 1) {
        fr"(mname = $name AND mvalue = ${values.head})"
      } else {
        val valueFragments = values.map(v => fr"$v")
        val inClause = valueFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
        fr"(mname = $name AND mvalue IN (" ++ inClause ++ fr"))"
      }
    }.reduceOption((a, b) => a ++ fr" OR " ++ b).getOrElse(fr"1=1")

    DoobieUtil.runQuery(
      (fr"SELECT mcode FROM mappedproductattribute WHERE mbankid = $bankId AND (" ++ filterFrag ++ fr")")
        .query[String].to[List])
  }
}
