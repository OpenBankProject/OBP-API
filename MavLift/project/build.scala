import sbt._
import Keys._
import com.github.siasia._
import PluginKeys._
import WebPlugin._
import WebappPlugin._

object LiftProjectBuild extends Build {
  override lazy val settings = super.settings ++ buildSettings
  
  lazy val buildSettings = Seq(
    organization := "com.tesobe",
    version      := "0.1",
    scalaVersion := "2.9.1")
  
  def yourWebSettings = webSettings ++ Seq(
    // If you are use jrebel
    scanDirectories in Compile := Nil
    )
  
  lazy val opanBank = Project(
    "OpanBank",
    base = file("."),
    settings = defaultSettings ++ yourWebSettings)
    
  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    name := "Opan Bank",
    resolvers ++= Seq(
      "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases", 
      "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
      "Scala-Tools Dependencies Repository for Releases" at "http://scala-tools.org/repo-releases",
      "Scala-Tools Dependencies Repository for Snapshots" at "http://scala-tools.org/repo-snapshots"),

    libraryDependencies ++= {
	  val liftVersion = "2.4-M4"
	  Seq(
	    //%% means: add current scala version to artifact name
	    
	    "net.liftweb" %% "lift-webkit" % liftVersion % "compile",
	    "net.liftweb" %% "lift-mapper" % liftVersion % "compile",
	    "net.liftweb" %% "lift-mongodb" % liftVersion % "compile",
	    "net.liftweb" %% "lift-mongodb-record" % liftVersion % "compile",
	    "net.liftweb" %% "lift-widgets" % liftVersion % "compile",	    
//	    "org.eclipse.jetty" % "jetty-webapp" % "7.5.4.v20111024" % "container",
//      "org.eclipse.jetty" % "jetty-webapp" % "8.0.4.v20111024" % "container",
        "org.mortbay.jetty" % "jetty" % "6.1.22" % "container",
        "javax.servlet" % "servlet-api" % "2.5" % "provided->default",	    
	    "ch.qos.logback" % "logback-classic" % "1.0.0" % "compile",
	    "com.h2database" % "h2" % "1.2.138" % "runtime",
	    "com.mongodb.casbah" %% "casbah" % "2.1.5-1" % "compile",	    
	    "org.scalatest" %% "scalatest" % "1.6.1" % "test",
	    "org.scala-tools.testing" %% "specs" % "1.6.9" % "test",
	    "junit" % "junit" % "4.10" % "test")
	},

    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    // show full stack traces
    testOptions in Test += Tests.Argument("-oF")
  )
  
}

