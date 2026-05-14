package code.routingscheme

import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

object MappedRoutingSchemeProvider extends RoutingSchemeProvider {

  // ── Routing scheme CRUD ────────────────────────────────────────────────────

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
  ): Box[RoutingSchemeTrait] = {
    tryo {
      RoutingScheme.create
        .Scheme(scheme)
        .Country(country)
        .Category(category)
        .AddressPattern(addressPattern)
        .SecondaryAddressPattern(secondaryAddressPattern.getOrElse(""))
        .ExampleAddress(exampleAddress)
        .Description(description)
        .DownstreamRails(downstreamRails.mkString(","))
        .Status(status)
        .CreatedByUserId(createdByUserId)
        .saveMe()
    }
  }

  override def getRoutingScheme(scheme: String): Box[RoutingSchemeTrait] =
    RoutingScheme.find(By(RoutingScheme.Scheme, scheme))

  override def getRoutingSchemes(
    country: Option[String],
    category: Option[String],
    status: Option[String],
    rail: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[RoutingSchemeTrait], Int)]] = Future {
    tryo {
      val baseQuery: List[QueryParam[RoutingScheme]] =
        country.map(c => By(RoutingScheme.Country, c)).toList :::
        category.map(c => By(RoutingScheme.Category, c)).toList :::
        status.map(s => By(RoutingScheme.Status, s)).toList
      // Count BEFORE applying limit/offset for total
      val total: Int = RoutingScheme.count(baseQuery: _*).toInt
      val rows: List[RoutingScheme] =
        RoutingScheme.findAll((baseQuery :+ OrderBy(RoutingScheme.Scheme, Ascending) :+ StartAt[RoutingScheme](offset) :+ MaxRows[RoutingScheme](limit)): _*)
      // Rail is a free-text tag list (CSV); filter in-memory after the SQL pass.
      val filtered = rail match {
        case Some(r) => rows.filter(_.downstreamRails.contains(r))
        case None    => rows
      }
      (filtered.asInstanceOf[List[RoutingSchemeTrait]], total)
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
  ): Box[RoutingSchemeTrait] = {
    RoutingScheme.find(By(RoutingScheme.Scheme, scheme)).flatMap { row =>
      tryo {
        addressPattern.foreach(v => row.AddressPattern(v))
        secondaryAddressPattern.foreach(v => row.SecondaryAddressPattern(v))
        exampleAddress.foreach(v => row.ExampleAddress(v))
        description.foreach(v => row.Description(v))
        downstreamRails.foreach(v => row.DownstreamRails(v.mkString(",")))
        status.foreach(v => row.Status(v))
        row.LastUpdate(new java.util.Date())
        row.saveMe()
      }
    }
  }

  override def deleteRoutingScheme(scheme: String): Box[Boolean] = {
    // Soft delete — set status to RETIRED, keep the row for historical resolution.
    RoutingScheme.find(By(RoutingScheme.Scheme, scheme)).flatMap { row =>
      tryo {
        row.Status("RETIRED").LastUpdate(new java.util.Date()).saveMe()
        true
      }
    }
  }

  // ── Bank-supported routing schemes ─────────────────────────────────────────

  override def getBankSupportedRoutingSchemes(bankId: String): Future[Box[List[BankSupportedRoutingSchemeTrait]]] = Future {
    tryo {
      BankSupportedRoutingScheme.findAll(By(BankSupportedRoutingScheme.BankId, bankId))
        .asInstanceOf[List[BankSupportedRoutingSchemeTrait]]
    }
  }

  override def putBankSupportedRoutingScheme(
    bankId: String,
    scheme: String,
    enabled: Boolean,
    bankNotes: Option[String]
  ): Box[BankSupportedRoutingSchemeTrait] = {
    val existing = BankSupportedRoutingScheme.find(
      By(BankSupportedRoutingScheme.BankId, bankId),
      By(BankSupportedRoutingScheme.Scheme, scheme)
    )
    tryo {
      existing match {
        case Full(row) =>
          row.Enabled(enabled)
            .BankNotes(bankNotes.getOrElse(""))
            .saveMe()
        case _ =>
          BankSupportedRoutingScheme.create
            .BankId(bankId)
            .Scheme(scheme)
            .Enabled(enabled)
            .BankNotes(bankNotes.getOrElse(""))
            .saveMe()
      }
    }
  }
}

class RoutingScheme extends RoutingSchemeTrait with LongKeyedMapper[RoutingScheme] with IdPK {
  def getSingleton = RoutingScheme

  object Scheme extends MappedString(this, 64)
  object Country extends MappedString(this, 8)            // alpha-2 or "INT" for global allow-list
  object Category extends MappedString(this, 16)          // ACCOUNT | BANK | BRANCH | IDENTITY | BILL | UTILITY
  object AddressPattern extends MappedString(this, 1024)
  object SecondaryAddressPattern extends MappedString(this, 1024)
  object ExampleAddress extends MappedString(this, 255)
  object Description extends MappedText(this)
  object DownstreamRails extends MappedString(this, 512)  // CSV: "TIPS,MNO_DIRECT"
  object Status extends MappedString(this, 16)            // ACTIVE | RESERVED | DEPRECATED | RETIRED
  object CreatedByUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def scheme: String = Scheme.get
  override def country: String = Country.get
  override def category: String = Category.get
  override def addressPattern: String = AddressPattern.get
  override def secondaryAddressPattern: Option[String] = {
    val v = SecondaryAddressPattern.get
    if (v == null || v.isEmpty) None else Some(v)
  }
  override def exampleAddress: String = ExampleAddress.get
  override def description: String = Description.get
  override def downstreamRails: List[String] = {
    val v = DownstreamRails.get
    if (v == null || v.isEmpty) Nil else v.split(",").toList.map(_.trim).filter(_.nonEmpty)
  }
  override def status: String = Status.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get
}

object RoutingScheme extends RoutingScheme with LongKeyedMetaMapper[RoutingScheme] {
  override def dbTableName = "RoutingScheme"
  override def dbIndexes = UniqueIndex(Scheme) :: super.dbIndexes
}

class BankSupportedRoutingScheme extends BankSupportedRoutingSchemeTrait with LongKeyedMapper[BankSupportedRoutingScheme] with IdPK {
  def getSingleton = BankSupportedRoutingScheme

  object BankId extends MappedString(this, 255)
  object Scheme extends MappedString(this, 64)
  object Enabled extends MappedBoolean(this) {
    override def defaultValue = true
  }
  object BankNotes extends MappedString(this, 1024)

  override def bankId: String = BankId.get
  override def scheme: String = Scheme.get
  override def enabled: Boolean = Enabled.get
  override def bankNotes: Option[String] = {
    val v = BankNotes.get
    if (v == null || v.isEmpty) None else Some(v)
  }
}

object BankSupportedRoutingScheme
    extends BankSupportedRoutingScheme
    with LongKeyedMetaMapper[BankSupportedRoutingScheme] {
  override def dbTableName = "BankSupportedRoutingScheme"
  override def dbIndexes =
    UniqueIndex(BankId, Scheme) :: Index(BankId) :: super.dbIndexes
}

object RoutingSchemeValidation {
  // Server-side guards. Mirrored in glossary + JSON-schema for clients.
  private val NameRegex     = "^(?:IBAN|BIC|OBP|[A-Z]{2}(?:\\.[A-Z][A-Z0-9_]*)+)$".r
  private val GlobalAllowList = Set("IBAN", "BIC", "OBP")
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
