package code.api.berlin.group.v1_3

import cats.data.OptionT
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.{ResourceDoc, berlinGroupV13AliasPath}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ScannedApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * http4s alias bridge for Berlin Group v1.3.
 *
 * When the `berlin_group_v1_3_alias_path` prop is set (e.g. "my-bank/v1.3"),
 * requests arriving at the alias prefix are path-rewritten to the canonical
 * `/berlin-group/v1.3/...` prefix and delegated to `Http4sBGv13.wrappedRoutes`.
 * This mirrors the behaviour of the Lift `OBP_BERLIN_GROUP_1_3_Alias` aggregator.
 *
 * When the prop is absent/empty, `wrappedRoutes` is `HttpRoutes.empty` (no-op).
 *
 * ResourceDocs are the same as the canonical BG v1.3 docs, re-stamped with the
 * alias `implementedInApiVersion` so they appear under the alias version in the
 * resource-docs endpoint.
 */
object Http4sBGv13Alias extends MdcLoggable {

  /** The alias ScannedApiVersion, matching OBP_BERLIN_GROUP_1_3_Alias.apiVersion. */
  val aliasVersion: ScannedApiVersion =
    if (berlinGroupV13AliasPath.nonEmpty)
      ScannedApiVersion(berlinGroupV13AliasPath.head, berlinGroupV13AliasPath.head, berlinGroupV13AliasPath.last)
    else
      ConstantsBG.berlinGroupVersion1 // inactive; value unused

  /**
   * ResourceDocs for the alias: the canonical BG v1.3 docs with
   * `implementedInApiVersion` overridden to the alias version.
   * Empty when the alias is not configured.
   */
  val resourceDocs: ArrayBuffer[ResourceDoc] =
    if (berlinGroupV13AliasPath.nonEmpty)
      Http4sBGv13.resourceDocs.map(doc =>
        doc.copy(implementedInApiVersion =
          aliasVersion.copy(apiStandard = doc.implementedInApiVersion.apiStandard)))
    else
      ArrayBuffer.empty[ResourceDoc]

  // e.g. "/berlin-group/v1.3"
  private val canonicalPrefixStr: String =
    s"/${ConstantsBG.berlinGroupVersion1.urlPrefix}/${ConstantsBG.berlinGroupVersion1.apiShortVersion}"

  // e.g. "/my-bank-group/v1.3"  (empty string when alias not configured)
  private val aliasPrefixStr: String =
    if (berlinGroupV13AliasPath.nonEmpty) "/" + berlinGroupV13AliasPath.mkString("/")
    else ""

  /**
   * Path-rewriting bridge routes.
   *
   * For each request whose path starts with the alias prefix:
   *   1. Strip the alias prefix.
   *   2. Prepend the canonical BG v1.3 prefix.
   *   3. Delegate the rewritten request to `Http4sBGv13.wrappedRoutes`.
   *
   * Falls through (`OptionT.none`) for paths that do not start with the alias
   * prefix, and is `HttpRoutes.empty` when the alias is not configured.
   */
  val wrappedRoutes: HttpRoutes[IO] =
    if (berlinGroupV13AliasPath.nonEmpty) {
      HttpRoutes[IO] { req =>
        val pathStr = req.uri.path.renderString
        if (pathStr.startsWith(aliasPrefixStr)) {
          val remainder     = pathStr.substring(aliasPrefixStr.length) // "" or "/..."
          val rewrittenPath = Uri.Path.unsafeFromString(canonicalPrefixStr + remainder)
          val rewrittenReq  = req.withUri(req.uri.copy(path = rewrittenPath))
          Http4sBGv13.wrappedRoutes.run(rewrittenReq)
        } else {
          OptionT.none
        }
      }
    } else {
      HttpRoutes.empty[IO]
    }
}
