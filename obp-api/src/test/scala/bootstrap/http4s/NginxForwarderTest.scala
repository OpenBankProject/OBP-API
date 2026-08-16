package bootstrap.http4s

import java.io.{File, FileOutputStream}
import java.net.URL
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.{HttpsURLConnection, KeyManagerFactory, SSLContext, TrustManagerFactory}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.{CertificateUtil, PeerTrust}
import code.api.util.http4s.{CallerCertificate, Http4sRequestAttributes, Psd2CertIngress}
import com.comcast.ip4s.{Host, Port}
import fs2.io.net.tls.{TLSContext, TLSParameters}
import org.http4s.{Header, HttpApp, Response, Status}
import org.http4s.ember.server.EmberServerBuilder
import org.scalatest.BeforeAndAfterAll
import org.typelevel.ci.CIString

import scala.sys.process._
import scala.util.Try
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * dev-behind-nginx (docs/MTLS_TOPOLOGIES.md §11.4 / §6.1): a REAL nginx in front of the REAL
 * caller-resolution middleware, so the four things that "nobody finds out until production" are
 * reproduced locally:
 *
 *   1. the header ENCODING — nginx forwards `$ssl_client_escaped_cert` (URL-encoded PEM); this is
 *      the only test that feeds Psd2CertIngress nginx's actual output rather than a hand-built value;
 *   2. the forwarder ALLOWLIST — a peer outside mtls.trusted_proxy.* is the caller, not a forwarder;
 *   3. SPOOFING through the proxy — a client-supplied PSD2-CERT must be overwritten;
 *   4. the missed-OVERWRITE misconfiguration — if nginx forwards the client's header instead of the
 *      verified certificate, the spoof wins. The harness must catch that, so it is asserted too.
 *
 * The upstream is the real middleware (Psd2CertIngress + CallerCertificate + PeerTrust) on a real
 * Ember server, not the whole of OBP-API: these four risks all live in that layer, and the DB /
 * consent / PSD2-gate weight is orthogonal.
 *
 * Requires Docker (the nginx image). Skipped, not failed, where Docker is unavailable, so a
 * checkout without it still builds.
 */
class NginxForwarderTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val NginxImage = "nginx:1.27-alpine"
  // An OS-assigned free port rather than a fixed one, so two runs on one host (e.g. parallel CI
  // shards) cannot collide; the container name carries it for the same reason.
  private val NginxPort = {
    val socket = new java.net.ServerSocket(0)
    try socket.getLocalPort finally socket.close()
  }
  private val NginxContainer = s"obp-nginx-forwarder-test-$NginxPort"
  private val Password = "123456"

  private val certDir = new File(getClass.getResource("/cert/dev-ca.crt").toURI).getParentFile
  private def cert(name: String): X509Certificate = {
    val f = new java.io.FileInputStream(new File(certDir, name))
    try java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(f).asInstanceOf[X509Certificate]
    finally f.close()
  }
  private lazy val tppPem = CertificateUtil.toPem(cert("tpp-client.crt"))
  private lazy val proxyPem = CertificateUtil.toPem(cert("proxy-client.crt"))

  private val dockerAvailable: Boolean = Try(Seq("docker", "version").! == 0).getOrElse(false)

  // nginx presents CN=nginx-dev-1 (proxy-client) to the upstream. The "trusted" branch lists it;
  // the "untrusted" branch has an empty allowlist, so nginx is treated as the caller.
  // Derive issuer AND subject from the real certificate: issuer is the CA (CN=OBP Dev CA), NOT the
  // proxy's own subject — for a CA-signed cert the two differ, and confusing them silently matches
  // nothing (which is exactly what the allowlist-rotation risk looks like in the field).
  private val trustsNginx = {
    val proxy = cert("proxy-client.crt")
    val issuer = PeerTrust.canonicalDn(proxy.getIssuerX500Principal.getName).get
    val subject = PeerTrust.canonicalDn(proxy.getSubjectX500Principal.getName).get
    PeerTrust.TrustConfig(List(PeerTrust.TrustedProxy(issuer, Some(subject))), trustForwardedHeaderWithoutTls = false)
  }
  private val emptyAllowlist = PeerTrust.TrustConfig(Nil, trustForwardedHeaderWithoutTls = false)

  // Echoes the caller PSD2-CERT the middleware resolved, with the trust decision in a header. The
  // path selects the trust config (nginx rewrites nothing: proxy_pass has no trailing slash).
  private val app: HttpApp[IO] = HttpApp[IO] { req =>
    val config = if (req.uri.path.renderString.startsWith("/untrusted")) emptyAllowlist else trustsNginx
    val resolved = CallerCertificate.resolveCaller(Psd2CertIngress.canonicalize(req), config)
    val caller = resolved.headers.get(CIString("PSD2-CERT")).map(_.head.value).getOrElse("NONE")
    val trust = resolved.attributes.lookup(Http4sRequestAttributes.callerCertificateTrustKey).map(_.describe).getOrElse("none")
    IO.pure(Response[IO](Status.Ok).withEntity(caller).putHeaders(Header.Raw(CIString("X-Trust"), trust)))
  }

  private var shutdown: IO[Unit] = IO.unit
  private var emberPort: Int = 0
  // True only once nginx is answering through the proxy. Environments where Docker exists but the
  // container cannot reach the host's loopback (--network host is a no-op on Docker Desktop for
  // Mac/Windows) skip the suite rather than fail it; the reason is printed by startNginx.
  private var nginxUp: Boolean = false

  override def beforeAll(): Unit = if (dockerAvailable) {
    // Upstream: OBP's server identity, requiring nginx to present a client cert it trusts (the CA).
    val sslContext = Http4sMtls.buildSslContext(Http4sMtls.MtlsConfig(
      keystorePath = new File(certDir, "obp-server.p12").getAbsolutePath, keystorePassword = Password,
      truststorePath = new File(certDir, "dev-truststore.p12").getAbsolutePath, truststorePassword = Password,
      needClientAuth = true))
    val (server, release) = EmberServerBuilder.default[IO]
      .withHost(Host.fromString("127.0.0.1").get).withPort(Port.fromInt(0).get)
      .withTLS(TLSContext.Builder.forAsync[IO].fromSSLContext(sslContext), TLSParameters(needClientAuth = true))
      .withHttpApp(app).build.allocated.unsafeRunSync()
    emberPort = server.address.getPort
    shutdown = release
    startNginx()
  }

  override def afterAll(): Unit = {
    if (dockerAvailable) Seq("docker", "rm", "-f", NginxContainer).run(ProcessLogger(_ => ())).exitValue()
    shutdown.unsafeRunSync()
  }

  private def startNginx(): Unit = {
    // proxy_pass without a trailing slash preserves the request URI, so the app can route on it.
    val conf =
      s"""events {}
         |http {
         |  upstream obp { server 127.0.0.1:$emberPort; }
         |  server {
         |    listen $NginxPort ssl;
         |    ssl_certificate           /certs/obp-server.crt;
         |    ssl_certificate_key       /certs/obp-server.key;
         |    ssl_client_certificate    /certs/dev-ca.crt;
         |    ssl_verify_client         on;
         |    proxy_ssl_certificate     /certs/proxy-client.crt;
         |    proxy_ssl_certificate_key /certs/proxy-client.key;
         |    proxy_ssl_verify          off;
         |
         |    # trusted forwarder, correct config: overwrite PSD2-CERT with the verified client cert
         |    location /trusted   { proxy_set_header PSD2-CERT $$ssl_client_escaped_cert; proxy_pass https://obp; }
         |    # same, but the app does not trust this proxy (empty allowlist)
         |    location /untrusted { proxy_set_header PSD2-CERT $$ssl_client_escaped_cert; proxy_pass https://obp; }
         |    # MISCONFIG: forwards the client's own header instead of the verified certificate
         |    location /missed    { proxy_set_header PSD2-CERT $$http_psd2_cert;          proxy_pass https://obp; }
         |  }
         |}
         |""".stripMargin
    val confFile = File.createTempFile("nginx-forwarder-", ".conf")
    confFile.deleteOnExit()
    val out = new FileOutputStream(confFile)
    try out.write(conf.getBytes("UTF-8")) finally out.close()

    Seq("docker", "rm", "-f", NginxContainer).run(ProcessLogger(_ => ())).exitValue()
    val run = Try(Seq("docker", "run", "--rm", "-d", "--name", NginxContainer, "--network", "host",
      "-v", s"${confFile.getAbsolutePath}:/etc/nginx/nginx.conf:ro",
      "-v", s"${certDir.getAbsolutePath}:/certs:ro",
      NginxImage).!!.trim)
    if (run.isFailure || run.get.length <= 10) {
      println(s"NginxForwarderTest: docker run failed (${run.fold(_.getMessage, identity)}) — suite will be skipped")
      return
    }

    // Wait for nginx to accept TLS on its port. Not coming up is a skip, not a failure: with
    // --network host unsupported (Docker Desktop) nginx can never reach the Ember upstream on the
    // host loopback, and that environment limitation should not read as a middleware regression.
    val deadline = System.currentTimeMillis() + 20000
    while (!nginxUp && System.currentTimeMillis() < deadline) {
      nginxUp = Try(get("/trusted", withClientCert = true)).isSuccess
      if (!nginxUp) Thread.sleep(500)
    }
    if (!nginxUp)
      println(s"NginxForwarderTest: nginx did not answer on $NginxPort within 20s " +
        "(no host networking for containers on this Docker?) — suite will be skipped")
  }

  private def clientContext(withClientCert: Boolean): SSLContext = {
    val trust = KeyStore.getInstance("JKS"); trust.load(null, null)
    trust.setCertificateEntry("ca", cert("dev-ca.crt"))
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm); tmf.init(trust)
    val kms = if (withClientCert) {
      val ks = KeyStore.getInstance("PKCS12")
      val in = new java.io.FileInputStream(new File(certDir, "tpp-client.p12"))
      try ks.load(in, Password.toCharArray) finally in.close()
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(ks, Password.toCharArray); kmf.getKeyManagers
    } else null
    val ctx = SSLContext.getInstance("TLS"); ctx.init(kms, tmf.getTrustManagers, null); ctx
  }

  private case class Resp(body: String, trust: String)
  private def get(path: String, withClientCert: Boolean, spoof: Option[String] = None): Resp = {
    val c = new URL(s"https://127.0.0.1:$NginxPort$path").openConnection().asInstanceOf[HttpsURLConnection]
    c.setSSLSocketFactory(clientContext(withClientCert).getSocketFactory)
    spoof.foreach(c.setRequestProperty("PSD2-CERT", _))
    try {
      val body = scala.io.Source.fromInputStream(c.getInputStream).mkString
      Resp(body, Option(c.getHeaderField("X-Trust")).getOrElse("none"))
    } finally c.disconnect()
  }

  // ---- the four risks, through real nginx --------------------------------------------------------

  "a request forwarded by a trusted nginx" should "resolve the caller from the URL-encoded PSD2-CERT" in {
    assume(nginxUp, "nginx harness not running (no Docker, or no host networking) — skipping")
    val r = get("/trusted", withClientCert = true)
    // nginx sent $ssl_client_escaped_cert (URL-encoded); Psd2CertIngress decoded it to canonical PEM.
    r.body shouldEqual tppPem
    r.trust should startWith("forwarded via")
  }

  "a client-supplied PSD2-CERT header" should "be overwritten by the verified certificate" in {
    assume(nginxUp, "nginx harness not running (no Docker, or no host networking) — skipping")
    val r = get("/trusted", withClientCert = true, spoof = Some("-----BEGIN CERTIFICATE-----SPOOF-----END CERTIFICATE-----"))
    r.body shouldEqual tppPem            // the TPP's real cert, not the spoof
    r.body should not include "SPOOF"
  }

  "a peer outside the allowlist" should "be treated as the caller, its forwarded header discarded" in {
    assume(nginxUp, "nginx harness not running (no Docker, or no host networking) — skipping")
    val r = get("/untrusted", withClientCert = true)
    r.body shouldEqual proxyPem          // nginx IS the caller now, not the forwarded TPP
    r.trust shouldEqual "direct"
  }

  "the missed-overwrite misconfiguration" should "let a spoofed header through — which is why overwrite matters" in {
    assume(nginxUp, "nginx harness not running (no Docker, or no host networking) — skipping")
    val r = get("/missed", withClientCert = true, spoof = Some("SPOOFED-CALLER-IDENTITY"))
    // nginx forwarded the CLIENT's header instead of the verified cert, and the app trusts nginx,
    // so the spoof reaches the caller identity. The harness exists to make this failure visible.
    r.body shouldEqual "SPOOFED-CALLER-IDENTITY"
  }
}
