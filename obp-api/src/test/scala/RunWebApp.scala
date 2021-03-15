/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

import bootstrap.liftweb.Boot
import code.api.util.APIUtil
import java.lang.reflect.{Proxy => JProxy}

import net.liftweb.http.LiftRules
import net.liftweb.http.provider.HTTPContext
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object RunWebApp extends App {
  val servletContextPath = "/"
  //set run mode value to "development", So the value is true of Props.devMode
  System.setProperty("run.mode", "development")

  /**
    * The above code is related to Chicken or the egg dilemma.
    * I.e. APIUtil.getPropsAsIntValue("dev.port", 8080) MUST be called after new Boot()
    * otherwise System.getProperty("props.resource.dir") is ignored.
    */
  val port: Int = {
    val tempHTTPContext = JProxy.newProxyInstance(this.getClass.getClassLoader, Array(classOf[HTTPContext]),
      (_, method, _) => {
        if (method.getName == "path") {
          servletContextPath
        } else {
          throw new IllegalAccessException(s"Should not call this object method except 'path' method, current call method name is: ${method.getName}")
          ??? // should not call other method.
        }
      }).asInstanceOf[HTTPContext]
    LiftRules.setContext(tempHTTPContext)

    new Boot()
    APIUtil.getPropsAsIntValue("dev.port", 8080)
  }

  val server = new Server(port)

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath(servletContextPath)
  // current project absolute path
  val basePath = this.getClass.getResource("/").toString .replaceFirst("target[/\\\\].*$", "")
  context.setWar(s"${basePath}src/main/webapp")
  // rename JSESSIONID, avoid conflict with other project when start two project at local
  context.getSessionHandler.getSessionCookieConfig.setName("JSESSIONID_OBP_API")
  server.setHandler(context)

  try {
    println(">>> STARTING EMBEDDED JETTY SERVER, PRESS ANY KEY TO STOP")
    server.start()
    while (System.in.available() == 0) {
      Thread.sleep(5000)
    }
    server.stop()
    server.join()
  } catch {
    case exc : Exception => {
      exc.printStackTrace()
      sys.exit(100)
    }
  }
}
