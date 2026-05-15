package code.routingscheme

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object RoutingSchemes extends SimpleInjector {
  val routingScheme = new Inject(buildOne _) {}

  def buildOne: RoutingSchemeProvider = MappedRoutingSchemeProvider
}

trait RoutingSchemeProvider {
  def createRoutingScheme(
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
  ): Box[RoutingSchemeTrait]

  def getRoutingScheme(scheme: String): Box[RoutingSchemeTrait]

  def getRoutingSchemes(
    country: Option[String],
    category: Option[String],
    status: Option[String],
    rail: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[RoutingSchemeTrait], Int)]]

  def updateRoutingScheme(
    scheme: String,
    addressPattern: Option[String],
    secondaryAddressPattern: Option[String],
    exampleAddress: Option[String],
    description: Option[String],
    downstreamRails: Option[List[String]],
    status: Option[String]
  ): Box[RoutingSchemeTrait]

  def deleteRoutingScheme(scheme: String): Box[Boolean]

  // ── Per-bank support ───────────────────────────────────────────────────────

  def getBankSupportedRoutingSchemes(bankId: String): Future[Box[List[BankSupportedRoutingSchemeTrait]]]

  def putBankSupportedRoutingScheme(
    bankId: String,
    scheme: String,
    enabled: Boolean,
    bankNotes: Option[String]
  ): Box[BankSupportedRoutingSchemeTrait]
}

trait RoutingSchemeTrait {
  def scheme: String
  def country: String
  def category: String
  def addressPattern: String
  def secondaryAddressPattern: Option[String]
  def exampleAddress: String
  def description: String
  def downstreamRails: List[String]
  def status: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}

trait BankSupportedRoutingSchemeTrait {
  def bankId: String
  def scheme: String
  def enabled: Boolean
  def bankNotes: Option[String]
}
