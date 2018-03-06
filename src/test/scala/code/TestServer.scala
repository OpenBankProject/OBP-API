package code

import code.api.util.APIUtil
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object TestServer {

  val host = "localhost"
  val port = APIUtil.getPropsAsIntValue("tests.port",8000)
  val externalHost = APIUtil.getPropsValue("external.hostname")
  val externalPort = APIUtil.getPropsAsIntValue("external.port")
  val server = new Server(port)

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath("/")
  context.setWar("src/main/webapp")

  server.setHandler(context)

  server.start()
}