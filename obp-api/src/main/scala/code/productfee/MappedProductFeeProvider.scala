package code.productfee

import code.api.util.ErrorMessages.{CreateProductFeeError, UpdateProductFeeError}
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, ProductCode, ProductFeeTrait}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import scala.math.BigDecimal

/**
 * One fee attached to a bank's product.
 *
 * The name is kept from the Lift entity: DeleteProductCascade and three historical
 * MigrationOf* scripts refer to it by name, and the row type is what ProductFeeTrait callers
 * already see through the provider.
 */
case class ProductFee(
  bankIdValue: String,
  productCodeValue: String,
  productFeeId: String,
  name: String,
  isActive: Boolean,
  moreInfo: String,
  currency: String,
  amount: BigDecimal,
  frequency: String,
  typeValue: String
) extends ProductFeeTrait {
  override def bankId: BankId = com.openbankproject.commons.model.BankId(bankIdValue)
  override def productCode: ProductCode = com.openbankproject.commons.model.ProductCode(productCodeValue)
  override def `type`: String = typeValue
}

object ProductFee {

  // Schemifier renames `type` to type_c because TYPE is a reserved word; the column really is
  // called type_c in the database.
  private val selectColumns =
    fr"""SELECT bankid, productcode, productfeeid, name, isactive, moreinfo, currency, amount,
                frequency, type_c
         FROM productfee"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[Boolean], Option[String], Option[String], Option[BigDecimal], Option[String],
    Option[String])

  private def fromRow(row: Row): ProductFee = row match {
    case (bankId, productCode, productFeeId, name, isActive, moreInfo, currency, amount, frequency, typeC) =>
        // MappedBoolean read a NULL column as false - `data openOr false`, with a NULL
        // setting `data = Empty` - so it never failed the read and never returned the
        // field's declared defaultValue. Binding the column as Option keeps both halves.
        //
        // MappedDecimal's JDBC setter is `if (isNull) defaultValue`, and its defaultValue is
        // `zero.setScale(scale)` - so a NULL amount read back as 0 at the column's scale, which
        // for NUMERIC(34, 2) is two places.
      ProductFee(bankId.orNull, productCode.orNull, productFeeId.orNull, name.orNull,
        isActive.getOrElse(false), moreInfo.orNull, currency.orNull,
        amount.getOrElse(BigDecimal(0).setScale(2)), frequency.orNull, typeC.orNull)
  }

  private def query(condition: Fragment): List[ProductFee] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAllByBankIdAndProductCode(bankId: String, productCode: String): List[ProductFee] =
    query(fr"WHERE bankid = $bankId AND productcode = $productCode")

  def findByProductFeeId(productFeeId: String): Box[ProductFee] =
    query(fr"WHERE productfeeid = $productFeeId LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(
    productFeeId: String, bankId: String, productCode: String, name: String, isActive: Boolean,
    moreInfo: String, currency: String, amount: BigDecimal, frequency: String, typeValue: String
  ): ProductFee = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO productfee
            (productfeeid, bankid, productcode, name, isactive, moreinfo, currency, amount, frequency, type_c)
            VALUES
            ($productFeeId, $bankId, $productCode, $name, $isActive, $moreInfo, $currency, $amount, $frequency, $typeValue)"""
        .update.run)
    ProductFee(bankId, productCode, productFeeId, name, isActive, moreInfo, currency, amount, frequency, typeValue)
  }

  def updateByProductFeeId(
    productFeeId: String, bankId: String, productCode: String, name: String, isActive: Boolean,
    moreInfo: String, currency: String, amount: BigDecimal, frequency: String, typeValue: String
  ): ProductFee = {
    DoobieUtil.runUpdate(
      sql"""UPDATE productfee SET bankid = $bankId, productcode = $productCode, name = $name,
              isactive = $isActive, moreinfo = $moreInfo, currency = $currency, amount = $amount,
              frequency = $frequency, type_c = $typeValue
            WHERE productfeeid = $productFeeId"""
        .update.run)
    ProductFee(bankId, productCode, productFeeId, name, isActive, moreInfo, currency, amount, frequency, typeValue)
  }

  def deleteByProductFeeId(productFeeId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM productfee WHERE productfeeid = $productFeeId".update.run)
    true
  }

  def deleteByBankIdAndProductCode(bankId: String, productCode: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM productfee WHERE bankid = $bankId AND productcode = $productCode".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM productfee".update.run)
    ()
  }
}

object MappedProductFeeProvider extends ProductFeeProvider {

  override def getProductFeesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductFeeTrait]]] =
    Future {
      Box !! ProductFee.findAllByBankIdAndProductCode(bankId.value, productCode.value)
    }

  override def getProductFeeById(productFeeId: String): Future[Box[ProductFeeTrait]] = Future {
    ProductFee.findByProductFeeId(productFeeId)
  }

  /**
   * A supplied productFeeId means update-that-row-or-Empty; no id means insert with a generated
   * one. Notably the update branch rewrites bankId and productCode too, so a fee can be moved
   * between products by id - preserved from the Mapper version.
   */
  override def createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    productFeeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String
  ): Future[Box[ProductFeeTrait]] = {
    productFeeId match {
      case Some(id) => Future {
        ProductFee.findByProductFeeId(id) match {
          case Full(_) => tryo {
            ProductFee.updateByProductFeeId(
              id, bankId.value, productCode.value, name, isActive, moreInfo, currency, amount, frequency, `type`)
          } ?~! s"$UpdateProductFeeError"
          case _ => Empty
        }
      }
      case None => Future {
        tryo {
          ProductFee.insert(
            APIUtil.generateUUID(), bankId.value, productCode.value, name, isActive, moreInfo,
            currency, amount, frequency, `type`)
        } ?~! s"$CreateProductFeeError"
      }
    }
  }

  override def deleteProductFee(productFeeId: String): Future[Box[Boolean]] = Future {
    tryo(ProductFee.deleteByProductFeeId(productFeeId))
  }
}
