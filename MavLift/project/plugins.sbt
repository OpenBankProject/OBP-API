resolvers += Classpaths.typesafeResolver

// addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse" % "1.5.0")

// resolvers += "sbt-deploy-repo" at "http://reaktor.github.com/sbt-deploy/maven"
// addSbtPlugin("fi.reaktor" %% "sbt-deploy" % "0.3.1-SNAPSHOT")

resolvers += "Web plugin repo" at "http://siasia.github.com/maven2"

libraryDependencies <+= sbtVersion(v => v match {
case "0.11.0" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.0-0.2.8"
case "0.11.1" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.1-0.2.10"
case "0.11.2" => "com.github.siasia" %% "xsbt-web-plugin" % "0.11.2-0.2.11"
})