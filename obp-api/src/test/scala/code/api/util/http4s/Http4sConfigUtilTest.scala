package code.api.util.http4s
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers


class Http4sConfigUtilTest extends AnyFlatSpec with Matchers {
  
  "parseHostname" should "extract hostname from plain IP address" in {
    Http4sConfigUtil.parseHostname("127.0.0.1") shouldBe "127.0.0.1"
  }
  
  it should "extract hostname from HTTP URI" in {
    Http4sConfigUtil.parseHostname("http://127.0.0.1:8080") shouldBe "127.0.0.1"
  }
  
  it should "extract hostname from HTTPS URI" in {
    Http4sConfigUtil.parseHostname("https://api.example.com") shouldBe "api.example.com"
  }
  
  it should "handle localhost" in {
    Http4sConfigUtil.parseHostname("localhost") shouldBe "localhost"
  }
  
  it should "handle URI with path" in {
    Http4sConfigUtil.parseHostname("http://example.com/path") shouldBe "example.com"
  }
  
  it should "trim whitespace" in {
    Http4sConfigUtil.parseHostname("  127.0.0.1  ") shouldBe "127.0.0.1"
  }
  
  it should "handle URI with port" in {
    Http4sConfigUtil.parseHostname("http://localhost:8080") shouldBe "localhost"
  }
  
  it should "handle domain names" in {
    Http4sConfigUtil.parseHostname("example.com") shouldBe "example.com"
  }
  
  it should "handle full URL with protocol, port and path" in {
    Http4sConfigUtil.parseHostname("https://api.example.com:443/v1/endpoint") shouldBe "api.example.com"
  }
}
