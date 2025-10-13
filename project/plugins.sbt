// SBT plugins for OBP project
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.5.6")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

// Scala compiler plugin for macros (equivalent to paradise plugin in Maven)
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

// SemanticDB for Metals support
addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.13.9" cross CrossVersion.full)
