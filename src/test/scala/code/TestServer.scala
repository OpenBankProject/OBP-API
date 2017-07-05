package code

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object TestServer {
  import net.liftweb.util.Props

  val host = "localhost"
  val port = Props.getInt("tests.port",8000)
  val externalHost = Props.get("external.hostname")
  val externalPort = Props.getInt("external.port")
  val server = new Server(port)

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath("/")
  context.setWar("src/main/webapp")

  server.setHandler(context)

  server.start()
}