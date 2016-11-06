import sbt._
import Keys._
import com.earldouglas.xwp._
import com.earldouglas.xwp.WebappPlugin
import com.earldouglas.xwp.ContainerPlugin.autoImport._


object LiftProjectBuild extends Build {
  override lazy val settings = super.settings ++ buildSettings

  lazy val buildSettings = Seq(
    organization  := pom.groupId,
    version       := pom.version
  )
  
  lazy val openBank = Project(
    pom.artifactId,
    base = file("."),
    settings = 	defaultSettings ++ pom.settings)
                .enablePlugins(JettyPlugin)

  object pom {

    val pomFile = "pom.xml"
    lazy val pom = xml.XML.loadFile(pomFile)

    lazy val pomProperties = (for{
      props <- (pom \ "properties")
      p <- props.child
    } yield {
      p.label -> p.text
    }).toMap

    private lazy val PropertiesExpr = """.*\$\{(.*?)\}.*""".r

    def populateProps(t: String) = t match {
      case PropertiesExpr(p) => {
        val replaceWith = pomProperties.get(p)
        t.replace("${"+p+"}", replaceWith.getOrElse(throw new Exception("Cannot find property: '" + p + "' required by: '" + t +  "' in: " + pomFile)))
      }
      case _ => t
    }

    lazy val pomDeps = (for{
      dep <- pom \ "dependencies" \ "dependency"
    } yield {
      val scope = (dep \ "scope")
      val groupId = (dep \ "groupId").text
      val noScope = populateProps(groupId) % populateProps((dep \ "artifactId").text) % populateProps((dep \ "version").text)
      val nonCustom = if (scope.nonEmpty) noScope  % populateProps(scope.text)
                      else noScope

      if (groupId.endsWith("jetty")) Seq(noScope % "container", nonCustom) //hack to add jetty deps in container scope as it is required by the web plugin
      else Seq(nonCustom)
    }).flatten

    lazy val pomRepos = for {
      rep <- pom \ "repositories" \ "repository"
    } yield {
      populateProps((rep \ "url").text) at populateProps((rep \ "url").text)
    }

    lazy val pomScalaVersion = (pom \ "properties" \ "scala.compiler").text

    lazy val artifactId = (pom \ "artifactId").text
    lazy val groupId = (pom \ "groupId").text
    lazy val version = (pom \ "version").text
    lazy val name = (pom \ "name").text

    lazy val settings = Seq(
      scalaVersion := pomScalaVersion,
      libraryDependencies ++= pomDeps,
      resolvers ++= pomRepos,
      containerPort := 8080
    )

  }
    
  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    name := pom.name,
    resolvers ++= Seq(
      "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases", 
      "Java.net Maven3 Repository" at "http://download.java.net/maven/3/",
      "Scala-Tools Dependencies Repository for Releases" at "http://scala-tools.org/repo-releases",
      "Scala-Tools Dependencies Repository for Snapshots" at "http://scala-tools.org/repo-snapshots"),

    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-Xmax-classfile-name", "78"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    // show full stack traces
    testOptions in Test += Tests.Argument("-oF")
  )
  
}

