package code.products

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{BankId, License, Meta, Product, ProductCode}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

/**
 * One product offered by a bank.
 *
 * `parentProductCode` models the hierarchy by value rather than by foreign key: getProductTree
 * walks it by repeatedly looking up (bankId, parentProductCode), and an empty string terminates
 * the walk. A product with no parent therefore stores "" and not NULL.
 */
case class MappedProduct(
  private val bankIdRaw: String,
  private val codeRaw: String,
  private val parentProductCodeRaw: String,
  name: String,
  category: String,
  family: String,
  superFamily: String,
  moreInfoUrl: String,
  termsAndConditionsUrl: String,
  details: String,
  description: String,
  private val licenseId: String,
  private val licenseName: String
) extends Product {
  // Every free-text column round-trips null as null, because callers really do pass null: the
  // v3.1.0 createProduct endpoint hands termsAndConditionsUrl the literal null. Lift's MappedString
  // stored that as SQL NULL and read it back as null; the store binds Option so it still does,
  // rather than throwing at bind time on a non-nullable Put.
  override def bankId: BankId = BankId(bankIdRaw)
  override def code: ProductCode = ProductCode(codeRaw)
  override def parentProductCode: ProductCode = ProductCode(parentProductCodeRaw)
  override def meta: Meta = Meta(license = License(id = licenseId, name = licenseName))
}

object MappedProduct {

  private val selectColumns =
    fr"""SELECT mbankid, mcode, mparentproductcode, mname, mcategory, mfamily, msuperfamily,
                mmoreinfourl, mtermsandconditionsurl, mdetails, mdescription, mlicenseid,
                mlicensename
         FROM mappedproduct"""

  // The free-text columns are read as Option and surfaced as null, mirroring what Lift's
  // MappedString did with a NULL column. Only the key columns and the parent code are non-null:
  // the parent code terminates the tree walk on "", so it must never be null.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedProduct = row match {
    case (bankId, code, parentProductCode, name, category, family, superFamily, moreInfoUrl,
          termsAndConditionsUrl, details, description, licenseId, licenseName) =>
      MappedProduct(bankId.orNull, code.orNull, parentProductCode.orNull, name.orNull,
        category.orNull, family.orNull, superFamily.orNull, moreInfoUrl.orNull,
        termsAndConditionsUrl.orNull, details.orNull, description.orNull, licenseId.orNull,
        licenseName.orNull)
  }

  private def query(condition: Fragment): List[MappedProduct] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(bankId: String, code: String): Box[MappedProduct] =
    query(fr"WHERE mbankid = $bankId AND mcode = $code ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAllByBankId(bankId: String): List[MappedProduct] =
    query(fr"WHERE mbankid = $bankId ORDER BY id ASC")

  def findAllByBankIdAndCodes(bankId: String, codes: List[String]): List[MappedProduct] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows — not "no filter".
    if (codes.isEmpty) Nil
    else {
      val in = Fragments.in(fr"mcode", cats.data.NonEmptyList.fromListUnsafe(codes.distinct))
      query(fr"WHERE mbankid = $bankId AND " ++ in ++ fr"ORDER BY id ASC")
    }

  def findAllByCodes(codes: List[String]): List[MappedProduct] =
    if (codes.isEmpty) Nil
    else {
      val in = Fragments.in(fr"mcode", cats.data.NonEmptyList.fromListUnsafe(codes.distinct))
      query(fr"WHERE " ++ in ++ fr"ORDER BY id ASC")
    }

  /**
   * Absent fields are stored as "" rather than NULL, which is what Mapper's untouched MappedString
   * defaults wrote and what every bare-String accessor expects to read back.
   */
  def createOrUpdate(bankId: String, code: String, parentProductCode: Option[String], name: String,
                     category: String, family: String, superFamily: String, moreInfoUrl: String,
                     termsAndConditionsUrl: String, details: String, description: String,
                     licenseId: String, licenseName: String): MappedProduct = {
    val existing = find(bankId, code)
    // parentProductCode is only written when supplied — an update that omits it leaves the stored
    // value alone, and a create that omits it gets the "" that terminates the tree walk.
    val parent = parentProductCode.orElse(existing.toOption.map(_.parentProductCode.value))
      .getOrElse("")
    if (existing.isDefined) {
      DoobieUtil.runUpdate(
        sql"""UPDATE mappedproduct SET mname = ${Option(name)}, mparentproductcode = $parent,
                mcategory = ${Option(category)}, mfamily = ${Option(family)},
                msuperfamily = ${Option(superFamily)}, mmoreinfourl = ${Option(moreInfoUrl)},
                mtermsandconditionsurl = ${Option(termsAndConditionsUrl)},
                mdetails = ${Option(details)}, mdescription = ${Option(description)},
                mlicenseid = ${Option(licenseId)}, mlicensename = ${Option(licenseName)}
              WHERE mbankid = $bankId AND mcode = $code""".update.run)
    } else {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedproduct
              (mbankid, mcode, mparentproductcode, mname, mcategory, mfamily, msuperfamily,
               mmoreinfourl, mtermsandconditionsurl, mdetails, mdescription, mlicenseid,
               mlicensename)
              VALUES ($bankId, $code, $parent, ${Option(name)}, ${Option(category)},
               ${Option(family)}, ${Option(superFamily)}, ${Option(moreInfoUrl)},
               ${Option(termsAndConditionsUrl)}, ${Option(details)}, ${Option(description)},
               ${Option(licenseId)}, ${Option(licenseName)})"""
          .update.run)
    }
    find(bankId, code).openOrThrowException("the product just written must be readable")
  }

  def delete(bankId: String, code: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedproduct WHERE mbankid = $bankId AND mcode = $code".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproduct".update.run)
    ()
  }
}

object MappedProductsProvider extends ProductsProvider {

  override protected def getProductFromProvider(bankId: BankId, productCode: ProductCode): Option[Product] =
    MappedProduct.find(bankId.value, productCode.value)

  override protected def getProductsFromProvider(bankId: BankId): Option[List[Product]] =
    Some(MappedProduct.findAllByBankId(bankId.value))
}
