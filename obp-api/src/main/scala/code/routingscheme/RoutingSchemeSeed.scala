package code.routingscheme

import net.liftweb.common.{Empty, Failure, Full}
import net.liftweb.util.SimpleInjector
import code.api.util.APIUtil
import net.liftweb.util.Helpers.tryo
import org.slf4j.LoggerFactory

/**
 * Idempotent seed of country-qualified routing schemes that ship with OBP.
 *
 * Called from Boot.scala once per process start. Each scheme is inserted only
 * if not already present, so re-running boot (or running multiple instances
 * concurrently) is safe.
 *
 * Toggle off in environments that don't want the defaults by setting the prop
 * `routing_schemes.seed_defaults_at_boot = false`.
 */
object RoutingSchemeSeed {

  private val logger = LoggerFactory.getLogger(getClass)

  // Pseudo-user for the createdByUserId column on seeded rows.
  private val SeedActor = "system:routing-scheme-seed"

  case class Entry(
    scheme: String,
    country: String,
    category: String,
    addressPattern: String,
    exampleAddress: String,
    description: String,
    downstreamRails: List[String]
  )

  // Global (unprefixed, country INT) schemes every OBP instance should know:
  // the three allow-listed international schemes used by bank/account
  // routing pairs (e.g. Open Corridor payment validation).
  val globalSeeds: List[Entry] = List(
    Entry("OBP",  "INT", "ACCOUNT",
      "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$", "gh.29.uk",
      "OBP bank id or account id, as used in OBP account routings.",
      Nil),
    Entry("IBAN", "INT", "ACCOUNT",
      "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$", "GB29NWBK60161331926819",
      "International Bank Account Number (ISO 13616), no spaces.",
      Nil),
    Entry("BIC",  "INT", "BANK",
      "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$", "NWBKGB2LXXX",
      "ISO 9362 Business Identifier Code, 8 or 11 characters.",
      Nil)
  )

  val tzSeeds: List[Entry] = List(
    Entry("TZ.MSISDN",              "TZ", "ACCOUNT",
      "^255[0-9]{9}$", "255778300336",
      "Tanzanian mobile number, E.164 without leading +. Used to identify a mobile-money wallet.",
      List("TIPS", "MNO_DIRECT")),
    Entry("TZ.FSP_ID",              "TZ", "BANK",
      "^[0-9]{3}$", "503",
      "TIPS Financial Service Provider code (3 digits).",
      List("TIPS")),
    Entry("TZ.NETWORK_PROVIDER",    "TZ", "BANK",
      "^[A-Z]+$", "PROVIDERA",
      "Mobile network operator short name (uppercase letters).",
      List("MNO_DIRECT")),
    Entry("TZ.BANK_ACCOUNT",        "TZ", "ACCOUNT",
      "^[0-9]{8,16}$", "24110000296",
      "Tanzanian domestic bank account number.",
      List("TIPS", "RTGS")),
    Entry("TZ.BANK_CODE",           "TZ", "BANK",
      "^[0-9]{3}$", "003",
      "Tanzanian domestic bank code (3 digits).",
      List("TIPS", "RTGS")),
    Entry("TZ.BRANCH_CODE",         "TZ", "BRANCH",
      "^[0-9]{3}$", "208",
      "Tanzanian branch routing code.",
      List("RTGS")),
    Entry("TZ.BILL_CONTROL_NUMBER", "TZ", "BILL",
      "^[0-9]{12}$", "991043383705",
      "Government / biller payment control number.",
      List("BILL")),
    Entry("TZ.BILL_SP_CODE",        "TZ", "BILL",
      "^SP[0-9]{5}$", "SP99103",
      "Biller service-provider code.",
      List("BILL")),
    Entry("TZ.UTILITY_METER",       "TZ", "UTILITY",
      "^[0-9]{8,14}$", "24730238417",
      "Prepaid electricity / utility meter number.",
      List("UTILITY")),
    Entry("TZ.NIN",                 "TZ", "IDENTITY",
      "^[0-9]{20}$", "19331007175010005135",
      "Tanzania National Identification Number.",
      Nil),
    Entry("TZ.TIN",                 "TZ", "IDENTITY",
      "^[0-9]{9}$", "123456789",
      "Tanzania Taxpayer Identification Number.",
      Nil),
    Entry("TZ.PASSPORT",            "TZ", "IDENTITY",
      "^[A-Z]{2}[0-9]{6,7}$", "AB068589",
      "Tanzanian passport number.",
      Nil)
  )

  def runIfEnabled(): Unit = {
    if (!APIUtil.getPropsAsBoolValue("routing_schemes.seed_defaults_at_boot", defaultValue = true)) {
      logger.info("[RoutingSchemeSeed] disabled via routing_schemes.seed_defaults_at_boot=false")
      return
    }
    val provider = MappedRoutingSchemeProvider
    val allSeeds = globalSeeds ++ tzSeeds
    val (inserted, skipped, failed) = allSeeds.foldLeft((0, 0, 0)) {
      case ((ins, skp, fld), entry) =>
        provider.getRoutingScheme(entry.scheme) match {
          case Full(_) =>
            (ins, skp + 1, fld) // already exists — skip
          case Empty | Failure(_, _, _) =>
            val r = tryo {
              provider.createRoutingScheme(
                scheme = entry.scheme,
                country = entry.country,
                category = entry.category,
                addressPattern = entry.addressPattern,
                secondaryAddressPattern = None,
                exampleAddress = entry.exampleAddress,
                description = entry.description,
                downstreamRails = entry.downstreamRails,
                status = "ACTIVE",
                createdByUserId = SeedActor
              )
            }
            r.flatten match {
              case Full(_) => (ins + 1, skp, fld)
              case _       => (ins, skp, fld + 1)
            }
        }
    }
    logger.info(s"[RoutingSchemeSeed] inserted=$inserted skipped=$skipped failed=$failed (of ${allSeeds.size} total seeds)")
  }
}
