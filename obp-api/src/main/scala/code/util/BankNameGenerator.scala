package code.util

import net.liftweb.common.{Box, Failure, Full}

import scala.util.Random

/**
 * Generates bank identities for self-service bank creation (POST /my/banks).
 *
 * Every public-facing string (bank_id, short name, full name) is produced here from
 * three curated word lists plus a 4-hex-char suffix, so no user-supplied text can
 * ever reach the anonymous GET /banks listing. Example output:
 *
 *   bankId    = "granite-astra-falcon-4f2a"
 *   shortName = "Granite Astra Falcon"
 *   fullName  = "Granite Astra Falcon Bank"
 *
 * Combination space: 100 x 100 x 100 x 16^4 = ~65 billion bank ids.
 *
 * Curation rules for the word lists (keep these when editing):
 *  - lowercase ASCII single words only (they become URL path segments)
 *  - no colour words that double as skin/ethnicity descriptors (black, white, brown,
 *    yellow, red) — the third word list contains nouns and the combinations would
 *    read as descriptions of people
 *  - no names of real financial institutions (e.g. fortis)
 *  - screen the full cross-product for rude substrings in the major sandbox-audience
 *    languages (en, de, fr, es, it) before adding words
 */
object BankNameGenerator {

  case class GeneratedBankName(bankId: String, shortName: String, fullName: String)

  // Colours, minerals, textures and qualities.
  val adjectives: List[String] = List(
    "amber", "coral", "indigo", "crimson", "teal", "cobalt", "jade", "slate", "ochre", "granite",
    "marble", "copper", "bronze", "silver", "golden", "pearl", "ivory", "onyx", "quartz", "topaz",
    "garnet", "opal", "amethyst", "sapphire", "emerald", "ruby", "turquoise", "magenta", "violet", "lavender",
    "lilac", "mauve", "maroon", "scarlet", "vermilion", "azure", "cerulean", "navy", "ultramarine", "aquamarine",
    "mint", "olive", "sage", "fern", "moss", "pine", "cedar", "oak", "maple", "birch",
    "walnut", "hazel", "chestnut", "sienna", "umber", "sepia", "taupe", "sandy", "dune", "clay",
    "terracotta", "brick", "flint", "basalt", "obsidian", "pumice", "shale", "cobble", "pebble", "crystal",
    "glacier", "arctic", "alpine", "polar", "boreal", "solar", "lunar", "stellar", "cosmic", "meridian",
    "zenith", "prime", "noble", "regal", "royal", "grand", "bright", "clear", "calm", "swift",
    "steady", "solid", "sturdy", "brisk", "bold", "keen", "deft", "agile", "gentle", "quiet"
  )

  // Latin and celestial nouns — loanword-grade / brand-familiar only.
  val celestials: List[String] = List(
    "nova", "terra", "luna", "astra", "stella", "aurora", "aqua", "vita", "lux", "pax",
    "vera", "alta", "magna", "prima", "ultra", "aura", "iris", "flora", "silva", "unda",
    "arbor", "avis", "apis", "ursa", "aquila", "corvus", "cygnus", "lyra", "vega", "altair",
    "sirius", "rigel", "polaris", "atlas", "titan", "helios", "selene", "gaia", "juno", "vesta",
    "ceres", "minerva", "aurelia", "cassia", "livia", "octavia", "nimbus", "cirrus", "stratus", "cumulus",
    "zephyr", "aether", "aurum", "argentum", "ferrum", "platina", "crux", "draco", "phoenix", "pegasus",
    "andromeda", "perseus", "calypso", "triton", "oberon", "titania", "miranda", "ariel", "callisto", "europa",
    "themis", "rhea", "dione", "janus", "orion", "castor", "pollux", "capella", "antares", "deneb",
    "mira", "electra", "maia", "celeste", "solis", "montis", "fontis", "pontis", "portus", "hortus",
    "domus", "virtus", "veritas", "concordia", "fortuna", "victoria", "gloria", "aurea", "argenta", "stellaris"
  )

  // Animals — birds, mammals and fish.
  val animals: List[String] = List(
    "falcon", "heron", "otter", "lynx", "ibex", "osprey", "kestrel", "merlin", "condor", "eagle",
    "hawk", "owl", "raven", "wren", "robin", "finch", "sparrow", "swallow", "starling", "crane",
    "stork", "pelican", "puffin", "gannet", "petrel", "albatross", "tern", "plover", "curlew", "avocet",
    "egret", "ibis", "flamingo", "toucan", "macaw", "kingfisher", "hoopoe", "lark", "nightingale", "oriole",
    "tanager", "warbler", "thrush", "magpie", "jay", "fox", "wolf", "bear", "elk", "moose",
    "stag", "hart", "bison", "buffalo", "yak", "chamois", "marmot", "beaver", "badger", "marten",
    "ermine", "hare", "rabbit", "squirrel", "hedgehog", "seal", "walrus", "dolphin", "porpoise", "whale",
    "orca", "narwhal", "manatee", "tortoise", "turtle", "gecko", "salmon", "trout", "pike", "perch",
    "carp", "sturgeon", "marlin", "tuna", "panther", "leopard", "cheetah", "jaguar", "puma", "cougar",
    "ocelot", "serval", "caracal", "lion", "tiger", "gazelle", "antelope", "oryx", "zebra", "okapi"
  )

  require(adjectives.distinct.size == adjectives.size, "duplicate word in adjectives list")
  require(celestials.distinct.size == celestials.size, "duplicate word in celestials list")
  require(animals.distinct.size == animals.size, "duplicate word in animals list")

  private val suffixAlphabet = "0123456789abcdef"
  private val suffixLength = 4

  private def pickRandomWord(words: List[String]): String = words(Random.nextInt(words.size))

  def generate(): GeneratedBankName = {
    val words = List(pickRandomWord(adjectives), pickRandomWord(celestials), pickRandomWord(animals))
    val suffix = List.fill(suffixLength)(suffixAlphabet(Random.nextInt(suffixAlphabet.length))).mkString
    val displayName = words.map(_.capitalize).mkString(" ")
    GeneratedBankName(
      bankId = (words :+ suffix).mkString("-"),
      shortName = displayName,
      fullName = s"$displayName Bank"
    )
  }

  /**
   * Generate a bank name whose bankId is not already taken, retrying up to maxAttempts
   * times. The caller supplies the taken-check (a lookup against the bank table); the
   * unique index on the bank_id column remains the correctness backstop for races.
   */
  def generateUnique(isBankIdTaken: String => Boolean, maxAttempts: Int = 10): Box[GeneratedBankName] = {
    val candidates = Iterator.continually(generate()).take(maxAttempts)
    candidates.find(candidate => !isBankIdTaken(candidate.bankId)) match {
      case Some(candidate) => Full(candidate)
      case None => Failure(s"Could not generate an unused bank id after $maxAttempts attempts")
    }
  }
}
