package code.routingscheme

import code.api.util.DoobieUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * A payment routing scheme.
 *
 * `scheme` is unique and is the handle every read, the update and the delete key off. Deletion is
 * soft: status goes to RETIRED and the row stays, so historical addresses can still be resolved.
 *
 * `secondaryAddressPattern` and `downstreamRails` hold "" rather than NULL when absent, which the
 * readers turn back into None and Nil.
 */
case class RoutingScheme(
  scheme: String,
  country: String,
  category: String,
  addressPattern: String,
  private val secondaryAddressPatternRaw: String,
  exampleAddress: String,
  description: String,
  private val downstreamRailsRaw: String,
  status: String,
  createdByUserId: String,
  createdAt: java.util.Date,
  updatedAt: java.util.Date
) extends RoutingSchemeTrait {

  override def secondaryAddressPattern: Option[String] =
    if (secondaryAddressPatternRaw == null || secondaryAddressPatternRaw.isEmpty) None
    else Some(secondaryAddressPatternRaw)

  override def downstreamRails: List[String] =
    if (downstreamRailsRaw == null || downstreamRailsRaw.isEmpty) Nil
    else downstreamRailsRaw.split(",").toList.map(_.trim).filter(_.nonEmpty)
}

object RoutingScheme {

  private val selectColumns =
    fr"""SELECT scheme, country, category, addresspattern, secondaryaddresspattern, exampleaddress,
                description, downstreamrails, status, createdbyuserid, creationdate, lastupdate
         FROM routingscheme"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def fromRow(row: Row): RoutingScheme = row match {
    case (scheme, country, category, addressPattern, secondaryAddressPattern, exampleAddress,
          description, downstreamRails, status, createdByUserId, creationDate, lastUpdate) =>
      RoutingScheme(scheme.orNull, country.orNull, category.orNull, addressPattern.orNull,
        secondaryAddressPattern.orNull, exampleAddress.orNull, description.orNull,
        downstreamRails.orNull, status.orNull, createdByUserId.orNull, creationDate.orNull,
        lastUpdate.orNull)
  }

  private def query(condition: Fragment): List[RoutingScheme] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(scheme: String, country: String, category: String, addressPattern: String,
             secondaryAddressPattern: String, exampleAddress: String, description: String,
             downstreamRails: String, status: String, createdByUserId: String): RoutingScheme = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO routingscheme
            (scheme, country, category, addresspattern, secondaryaddresspattern, exampleaddress,
             description, downstreamrails, status, createdbyuserid, creationdate, lastupdate)
            VALUES ($scheme, $country, $category, $addressPattern, $secondaryAddressPattern,
             $exampleAddress, $description, $downstreamRails, $status, $createdByUserId, $now,
             $now)"""
        .update.run)
    findByScheme(scheme).openOrThrowException("the routing scheme just inserted must be readable")
  }

  def findByScheme(scheme: String): Box[RoutingScheme] =
    query(fr"WHERE scheme = $scheme ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  private def filters(country: Option[String], category: Option[String],
                      status: Option[String]): Fragment = {
    val conditions = List(
      country.map(v => fr"country = $v"),
      category.map(v => fr"category = $v"),
      status.map(v => fr"status = $v")
    ).flatten
    if (conditions.isEmpty) Fragment.empty
    else fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
  }

  /** The total BEFORE limit and offset, so the caller can page. */
  def countFiltered(country: Option[String], category: Option[String], status: Option[String]): Int =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM routingscheme" ++ filters(country, category, status))
        .query[Int].unique)

  def findPage(country: Option[String], category: Option[String], status: Option[String],
               limit: Int, offset: Int): List[RoutingScheme] =
    query(filters(country, category, status) ++ fr"ORDER BY scheme ASC LIMIT $limit OFFSET $offset")

  /** Only the supplied fields change; lastupdate is always stamped. */
  def update(scheme: String, addressPattern: Option[String],
             secondaryAddressPattern: Option[String], exampleAddress: Option[String],
             description: Option[String], downstreamRails: Option[String],
             status: Option[String]): Box[RoutingScheme] = {
    val sets = List(
      addressPattern.map(v => fr"addresspattern = $v"),
      secondaryAddressPattern.map(v => fr"secondaryaddresspattern = $v"),
      exampleAddress.map(v => fr"exampleaddress = $v"),
      description.map(v => fr"description = $v"),
      downstreamRails.map(v => fr"downstreamrails = $v"),
      status.map(v => fr"status = $v")
    ).flatten :+ fr"lastupdate = ${new java.sql.Timestamp(System.currentTimeMillis())}"
    DoobieUtil.runUpdate(
      (fr"UPDATE routingscheme SET" ++ sets.reduce((a, b) => a ++ fr"," ++ b) ++
        fr"WHERE scheme = $scheme").update.run)
    findByScheme(scheme)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM routingscheme".update.run)
    ()
  }
}

/** Whether a bank supports a routing scheme. (bankid, scheme) is unique, which is what makes the
 *  provider's put an upsert rather than an append. */
case class BankSupportedRoutingScheme(
  bankId: String,
  scheme: String,
  enabled: Boolean,
  private val bankNotesRaw: String
) extends BankSupportedRoutingSchemeTrait {
  override def bankNotes: Option[String] =
    if (bankNotesRaw == null || bankNotesRaw.isEmpty) None else Some(bankNotesRaw)
}

object BankSupportedRoutingScheme {

  private val selectColumns =
    fr"SELECT bankid, scheme, enabled, banknotes FROM banksupportedroutingscheme"

  private def query(condition: Fragment): List[BankSupportedRoutingScheme] =
    DoobieUtil.runQuery(
      (selectColumns ++ condition).query[(String, String, Option[Boolean], String)].to[List])
      .map { case (bankId, scheme, enabled, bankNotes) =>
        // MappedBoolean read a NULL column as false, never as the declared defaultValue.
        BankSupportedRoutingScheme(bankId, scheme, enabled.getOrElse(false), bankNotes) }

  def findAllByBankId(bankId: String): List[BankSupportedRoutingScheme] =
    query(fr"WHERE bankid = $bankId ORDER BY id ASC")

  def find(bankId: String, scheme: String): Box[BankSupportedRoutingScheme] =
    query(fr"WHERE bankid = $bankId AND scheme = $scheme ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def upsert(bankId: String, scheme: String, enabled: Boolean,
             bankNotes: String): BankSupportedRoutingScheme = {
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE banksupportedroutingscheme SET enabled = $enabled, banknotes = $bankNotes
            WHERE bankid = $bankId AND scheme = $scheme""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO banksupportedroutingscheme (bankid, scheme, enabled, banknotes)
              VALUES ($bankId, $scheme, $enabled, $bankNotes)"""
          .update.run)
    }
    BankSupportedRoutingScheme(bankId, scheme, enabled, bankNotes)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM banksupportedroutingscheme".update.run)
    ()
  }
}

object MappedRoutingSchemeProvider extends RoutingSchemeProvider {

  override def createRoutingScheme(
    scheme: String,
    country: String,
    category: String,
    addressPattern: String,
    secondaryAddressPattern: Option[String],
    exampleAddress: String,
    description: String,
    downstreamRails: List[String],
    status: String,
    createdByUserId: String
  ): Box[RoutingSchemeTrait] =
    tryo {
      RoutingScheme.insert(scheme, country, category, addressPattern,
        secondaryAddressPattern.getOrElse(""), exampleAddress, description,
        downstreamRails.mkString(","), status, createdByUserId)
    }

  override def getRoutingScheme(scheme: String): Box[RoutingSchemeTrait] =
    RoutingScheme.findByScheme(scheme)

  override def getRoutingSchemes(
    country: Option[String],
    category: Option[String],
    status: Option[String],
    rail: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[RoutingSchemeTrait], Int)]] = Future {
    tryo {
      // Count BEFORE applying limit/offset for total
      val total = RoutingScheme.countFiltered(country, category, status)
      val rows = RoutingScheme.findPage(country, category, status, limit, offset)
      // Rail is a free-text tag list (CSV); filter in-memory after the SQL pass.
      val filtered = rail match {
        case Some(r) => rows.filter(_.downstreamRails.contains(r))
        case None    => rows
      }
      (filtered, total)
    }
  }

  override def updateRoutingScheme(
    scheme: String,
    addressPattern: Option[String],
    secondaryAddressPattern: Option[String],
    exampleAddress: Option[String],
    description: Option[String],
    downstreamRails: Option[List[String]],
    status: Option[String]
  ): Box[RoutingSchemeTrait] =
    RoutingScheme.findByScheme(scheme).flatMap { _ =>
      tryo {
        RoutingScheme.update(scheme, addressPattern, secondaryAddressPattern, exampleAddress,
          description, downstreamRails.map(_.mkString(",")), status)
      }.flatMap(identity)
    }

  override def deleteRoutingScheme(scheme: String): Box[Boolean] =
    // Soft delete — set status to RETIRED, keep the row for historical resolution.
    RoutingScheme.findByScheme(scheme).flatMap { _ =>
      tryo {
        RoutingScheme.update(scheme, None, None, None, None, None, Some("RETIRED"))
        true
      }
    }

  override def getBankSupportedRoutingSchemes(bankId: String): Future[Box[List[BankSupportedRoutingSchemeTrait]]] =
    Future(tryo(BankSupportedRoutingScheme.findAllByBankId(bankId)))

  override def putBankSupportedRoutingScheme(
    bankId: String,
    scheme: String,
    enabled: Boolean,
    bankNotes: Option[String]
  ): Box[BankSupportedRoutingSchemeTrait] =
    tryo(BankSupportedRoutingScheme.upsert(bankId, scheme, enabled, bankNotes.getOrElse("")))
}

object RoutingSchemeValidation {
  // Server-side guards. Mirrored in glossary + JSON-schema for clients.
  // CARDANO / ETHEREUM: global blockchain rails (country INT). CARDANO carries
  // the Open Corridor settlement address as an account routing on
  // OBP-INCOMING-SETTLEMENT-ACCOUNT; ETHEREUM is allowlisted for the same use
  // when a second rail backend exists.
  private val NameRegex     = "^(?:IBAN|BIC|OBP|CARDANO|ETHEREUM|[A-Z]{2}(?:\\.[A-Z][A-Z0-9_]*)+)$".r
  private val GlobalAllowList = Set("IBAN", "BIC", "OBP", "CARDANO", "ETHEREUM")
  val ValidCategories: Set[String] = Set("ACCOUNT", "BANK", "BRANCH", "IDENTITY", "BILL", "UTILITY")
  val ValidStatuses: Set[String]   = Set("ACTIVE", "RESERVED", "DEPRECATED", "RETIRED")

  def isValidSchemeName(s: String): Boolean = NameRegex.findFirstIn(s).isDefined

  /** The country prefix in the scheme name must match the `country` field.
   *  Globally-unique allow-listed schemes (IBAN/BIC/OBP) must use country "INT".
   */
  def countryMatchesPrefix(scheme: String, country: String): Boolean = {
    if (GlobalAllowList.contains(scheme)) country == "INT"
    else scheme.split("\\.", 2).headOption.contains(country)
  }

  def isValidRegex(pattern: String): Boolean =
    Try(java.util.regex.Pattern.compile(pattern)) match {
      case Success(_) => true
      case Failure(_) => false
    }

  /** Returns true if `address` matches `pattern`. False if pattern itself is invalid. */
  def addressMatchesPattern(pattern: String, address: String): Boolean =
    Try(java.util.regex.Pattern.compile(pattern).matcher(address).matches()).getOrElse(false)
}
