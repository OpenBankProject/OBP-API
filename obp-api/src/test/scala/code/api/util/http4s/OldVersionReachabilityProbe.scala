package code.api.util.http4s

import cats.effect.IO
import code.api.util.APIUtil.ResourceDoc
import code.setup.ServerSetup
import org.http4s.{Method, Request, Uri}

import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer

/**
 * Analysis probe (not for commit): for every ResourceDoc of v1.2.1 .. v5.0.0, does a request under a kept
 * prefix (v5.1.0, v6.0.0, v7.0.0) reach it through the path-rewriting bridges?
 *
 * The cascade is replayed with ResourceDocMatcher.selectByRoute over each version's ordered docs, in the
 * order Http4s700 / Http4s600 / Http4s510 / Http4s500 / ... try them, including v7.0.0's excludeEndpoints.
 * Output: a TSV file named by the system property or env var OBP_PROBE_OUT.
 */
class OldVersionReachabilityProbe extends ServerSetup {

  private def chain: List[(String, ArrayBuffer[ResourceDoc])] = List(
    "v7.0.0" -> code.api.v7_0_0.Http4s700.Implementations7_0_0.orderedResourceDocs,
    "v6.0.0" -> code.api.v6_0_0.Http4s600.Implementations6_0_0.orderedResourceDocs,
    "v5.1.0" -> code.api.v5_1_0.Http4s510.Implementations5_1_0.orderedResourceDocs,
    "v5.0.0" -> code.api.v5_0_0.Http4s500.Implementations5_0_0.orderedResourceDocs,
    "v4.0.0" -> code.api.v4_0_0.Http4s400.Implementations4_0_0.orderedResourceDocs,
    "v3.1.0" -> code.api.v3_1_0.Http4s310.Implementations3_1_0.orderedResourceDocs,
    "v3.0.0" -> code.api.v3_0_0.Http4s300.Implementations3_0_0.orderedResourceDocs,
    "v2.2.0" -> code.api.v2_2_0.Http4s220.Implementations2_2_0.orderedResourceDocs,
    "v2.1.0" -> code.api.v2_1_0.Http4s210.Implementations2_1_0.orderedResourceDocs,
    "v2.0.0" -> code.api.v2_0_0.Http4s200.Implementations2_0_0.orderedResourceDocs,
    "v1.4.0" -> code.api.v1_4_0.Http4s140.Implementations1_4_0.orderedResourceDocs,
    "v1.3.0" -> code.api.v1_3_0.Http4s130.Implementations1_3_0.orderedResourceDocs,
    "v1.2.1" -> code.api.v1_2_1.Http4s121.Implementations1_2_1.orderedResourceDocs
  )

  private def describe(d: ResourceDoc): String =
    s"${d.implementedInApiVersion.apiShortVersion}:${d.partialFunctionName}"

  feature("Old-version reachability") {
    scenario("replay the cascade for every old doc") {
      val versions = chain
      val indexes = versions.map { case (v, docs) => v -> ResourceDocMatcher.buildRouteIndex(docs) }
      val excludedNames = code.api.v7_0_0.Http4s700.excludeEndpoints.toSet
      val allOld = versions.drop(3).flatMap(_._2)
      val excludedDocs = allOld.filter(d => excludedNames.contains(d.partialFunctionName))
      val excludedIndex = ResourceDocMatcher.buildRouteIndex(excludedDocs)
      val excludedVersions = excludedDocs.map(_.implementedInApiVersion.apiShortVersion).distinct

      def req(verb: String, version: String, url: String): Request[IO] =
        Request[IO](Method.fromString(verb.toUpperCase).fold(throw _, identity), Uri.unsafeFromString(s"/obp/$version$url"))

      // Returns the doc the cascade selects, starting at `start`, or None (JSON 404).
      def cascade(verb: String, url: String, start: String): Option[ResourceDoc] = {
        val from = indexes.indexWhere(_._1 == start)
        var i = from
        var result: Option[ResourceDoc] = None
        var stop = false
        while (!stop && i < indexes.length) {
          val (v, index) = indexes(i)
          ResourceDocMatcher.selectByRoute(req(verb, v, url), index) match {
            case Some(d) => result = Some(d); stop = true
            case None =>
              if (v == "v7.0.0" && excludedVersions.exists(ev =>
                ResourceDocMatcher.selectByRoute(req(verb, ev, url), excludedIndex).nonEmpty)) stop = true
              else i += 1
          }
        }
        result
      }

      val kept = List("v7.0.0", "v6.0.0", "v5.1.0")
      val out = new PrintWriter(Option(System.getProperty("OBP_PROBE_OUT")).orElse(sys.env.get("OBP_PROBE_OUT")).getOrElse("/tmp/obp_probe.tsv"))
      out.println(List("version", "name", "operation_id", "verb", "url", "reached_via", "self_selected", "route_id",
        "v510_selects", "v600_selects", "v700_selects").mkString("\t"))
      allOld.foreach { d =>
        val verb = d.requestVerb.toUpperCase
        val sels = kept.map(p => p -> cascade(verb, d.requestUrl, p))
        val reachedVia = sels.collect { case (p, Some(s)) if s eq d => p }
        val self = ResourceDocMatcher.selectByRoute(req(verb, d.implementedInApiVersion.apiShortVersion, d.requestUrl),
          indexes.find(_._1 == d.implementedInApiVersion.apiShortVersion).get._2).exists(_ eq d)
        val routeId = d.http4sPartialFunction.flatMap(Option(_)).map(h => System.identityHashCode(h.routes)).getOrElse(0)
        def show(p: String) = sels.find(_._1 == p).flatMap(_._2).map(describe).getOrElse("404")
        out.println(List(d.implementedInApiVersion.apiShortVersion, d.partialFunctionName, d.operationId, verb, d.requestUrl,
          reachedVia.mkString(","), self.toString, routeId.toString, show("v5.1.0"), show("v6.0.0"), show("v7.0.0")).mkString("\t"))
      }
      // The kept catalogue, for the replacement check.
      versions.take(3).foreach { case (v, docs) =>
        docs.foreach(d => out.println(List(v, d.partialFunctionName, d.operationId, d.requestVerb.toUpperCase, d.requestUrl,
          "KEPT", "", "", "", "", "").mkString("\t")))
      }
      List(code.api.berlin.group.v1_3.Http4sBGv13.orderedResourceDocs, code.api.berlin.group.v2.Http4sBGv2.orderedResourceDocs,
        code.api.UKOpenBanking.v2_0_0.Http4sUKOBv200.orderedResourceDocs, code.api.UKOpenBanking.v3_1_0.Http4sUKOBv310.orderedResourceDocs,
        code.api.UKOpenBanking.v4_0_1.Http4sUKOBv401.orderedResourceDocs).flatten.foreach { d =>
        out.println(List(d.implementedInApiVersion.toString, d.partialFunctionName, d.operationId, d.requestVerb.toUpperCase, d.requestUrl,
          "STANDARD", "", "", "", "", "").mkString("\t"))
      }
      out.close()
      allOld should not be empty
    }
  }
}
