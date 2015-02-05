package code

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object TestServer {
  import net.liftweb.util.Props

  val host = "localhost"
  val port = Props.getInt("tests.port",8000)
  val server = new Server(port)

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath("/")
  context.setWar("src/main/webapp")

  server.setHandler(context)

  server.start()
}