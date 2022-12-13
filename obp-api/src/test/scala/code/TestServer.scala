package code

import code.api.util.APIUtil
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

import java.util.UUID

object TestServer {

  val host = "localhost"
  val port = APIUtil.getPropsAsIntValue("tests.port",8000)
  val externalHost = APIUtil.getPropsValue("external.hostname")
  val externalPort = APIUtil.getPropsAsIntValue("external.port")
  val server = new Server(port)

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath("/")
  val basePath = this.getClass.getResource("/").toString .replaceFirst("target[/\\\\].*$", "")
  context.setWar(s"${basePath}src/main/webapp")

  server.setHandler(context)

  server.start()

  val userId1 = Some(UUID.randomUUID.toString)
  val userId2 = Some(UUID.randomUUID.toString)
  val userId3 = Some(UUID.randomUUID.toString)
  val userId4 = Some(UUID.randomUUID.toString)

  val resourceUser1Name = "resourceUser1"
  val resourceUser2Name = "resourceUser2"
  val resourceUser3Name = "resourceUser3"
  val resourceUser4Name = "resourceUser4"
  
}