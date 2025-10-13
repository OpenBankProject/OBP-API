ThisBuild / version := "1.10.1"
ThisBuild / scalaVersion := "2.12.20"
ThisBuild / organization := "com.tesobe"

// Java version compatibility
ThisBuild / javacOptions ++= Seq("-source", "11", "-target", "11")
ThisBuild / scalacOptions ++= Seq(
  "-unchecked",
  "-explaintypes",
  "-target:jvm-1.8",
  "-Yrangepos"
)

// Enable SemanticDB for Metals
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := "4.13.9"

// Fix dependency conflicts
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

lazy val liftVersion = "3.5.0"
lazy val akkaVersion = "2.5.32"
lazy val jettyVersion = "9.4.50.v20221201"
lazy val avroVersion = "1.8.2"

lazy val commonSettings = Seq(
  resolvers ++= Seq(
    "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Artima Maven Repository" at "https://repo.artima.com/releases",
    "OpenBankProject M2 Repository" at "https://raw.githubusercontent.com/OpenBankProject/OBP-M2-REPO/master",
    "jitpack.io" at "https://jitpack.io"
  )
)

lazy val obpCommons = (project in file("obp-commons"))
  .settings(
    commonSettings,
    name := "obp-commons",
    libraryDependencies ++= Seq(
      "net.liftweb" %% "lift-common" % liftVersion,
      "net.liftweb" %% "lift-util" % liftVersion,
      "net.liftweb" %% "lift-mapper" % liftVersion,
      "org.scala-lang" % "scala-reflect" % "2.12.20",
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
      "org.scalactic" %% "scalactic" % "3.2.15",
      "net.liftweb" %% "lift-json" % liftVersion,
      "com.alibaba" % "transmittable-thread-local" % "2.11.5",
      "org.apache.commons" % "commons-lang3" % "3.12.0",
      "org.apache.commons" % "commons-text" % "1.10.0",
      "com.google.guava" % "guava" % "32.0.0-jre"
    )
  )

lazy val obpApi = (project in file("obp-api"))
  .dependsOn(obpCommons)
  .settings(
    commonSettings,
    name := "obp-api",
    libraryDependencies ++= Seq(
      // Core dependencies
      "net.liftweb" %% "lift-mapper" % liftVersion,
      "net.databinder.dispatch" %% "dispatch-lift-json" % "0.13.1",
      "ch.qos.logback" % "logback-classic" % "1.2.13",
      "org.slf4j" % "log4j-over-slf4j" % "1.7.26",
      "org.slf4j" % "slf4j-ext" % "1.7.26",
      
      // Security
      "org.bouncycastle" % "bcpg-jdk15on" % "1.70",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.70",
      "com.nimbusds" % "nimbus-jose-jwt" % "9.37.2",
      "com.nimbusds" % "oauth2-oidc-sdk" % "9.27",
      
      // Commons
      "org.apache.commons" % "commons-lang3" % "3.12.0",
      "org.apache.commons" % "commons-text" % "1.10.0",
      "org.apache.commons" % "commons-email" % "1.5",
      "org.apache.commons" % "commons-compress" % "1.26.0",
      "org.apache.commons" % "commons-pool2" % "2.11.1",
      
      // Database
      "org.postgresql" % "postgresql" % "42.4.4",
      "com.h2database" % "h2" % "2.2.220" % Runtime,
      "mysql" % "mysql-connector-java" % "8.0.30",
      "com.microsoft.sqlserver" % "mssql-jdbc" % "11.2.0.jre11",
      
      // Web
      "javax.servlet" % "javax.servlet-api" % "3.1.0" % Provided,
      "org.eclipse.jetty" % "jetty-server" % jettyVersion % Test,
      "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % Test,
      "org.eclipse.jetty" % "jetty-util" % jettyVersion,
      
      // Akka
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % "10.1.6",
      
      // Avro
      "com.sksamuel.avro4s" %% "avro4s-core" % avroVersion,
      
      // Twitter
      "com.twitter" %% "chill-akka" % "0.9.1",
      "com.twitter" %% "chill-bijection" % "0.9.1",
      
      // Cache
      "com.github.cb372" %% "scalacache-redis" % "0.9.3",
      "com.github.cb372" %% "scalacache-guava" % "0.9.3",
      
      // Utilities
      "com.github.dwickern" %% "scala-nameof" % "1.0.3",
      "org.javassist" % "javassist" % "3.25.0-GA",
      "com.alibaba" % "transmittable-thread-local" % "2.14.2",
      "org.clapper" %% "classutil" % "1.4.0",
      "com.github.grumlimited" % "geocalc" % "0.5.7",
      "com.github.OpenBankProject" % "scala-macros" % "v1.0.0-alpha.3",
      "org.scalameta" %% "scalameta" % "3.7.4",
      
      // Akka Adapter - exclude transitive dependency on obp-commons to use local module
      "com.github.OpenBankProject.OBP-Adapter-Akka-SpringBoot" % "adapter-akka-commons" % "v1.1.0" exclude("com.github.OpenBankProject.OBP-API", "obp-commons"),
    
      // JSON Schema
      "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.6.1",
      "com.networknt" % "json-schema-validator" % "1.0.87",
      
      // Swagger
      "io.swagger.parser.v3" % "swagger-parser" % "2.0.13",
      
      // Text processing
      "org.atteo" % "evo-inflector" % "1.2.2",
      
      // Payment
      "com.stripe" % "stripe-java" % "12.1.0",
      "com.twilio.sdk" % "twilio" % "9.2.0",
      
      // gRPC
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % "0.8.4",
      "io.grpc" % "grpc-all" % "1.48.1",
      "io.netty" % "netty-tcnative-boringssl-static" % "2.0.27.Final",
      "org.asynchttpclient" % "async-http-client" % "2.10.4",
      
      // Database utilities
      "org.scalikejdbc" %% "scalikejdbc" % "3.4.0",
      
      // XML
      "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
      
      // IBAN
      "org.iban4j" % "iban4j" % "3.2.7-RELEASE",
      
      // JavaScript
      "org.graalvm.js" % "js" % "22.0.0.2",
      "org.graalvm.js" % "js-scriptengine" % "22.0.0.2",
      "ch.obermuhlner" % "java-scriptengine" % "2.0.0",
      
      // Hydra
      "sh.ory.hydra" % "hydra-client" % "1.7.0",
      
      // HTTP
      "com.squareup.okhttp3" % "okhttp" % "4.12.0",
      "com.squareup.okhttp3" % "logging-interceptor" % "4.12.0",
      "org.apache.httpcomponents" % "httpclient" % "4.5.13",
      
      // RabbitMQ
      "com.rabbitmq" % "amqp-client" % "5.22.0",
      "net.liftmodules" %% "amqp_3.1" % "1.5.0",
      
      // Elasticsearch
      "org.elasticsearch" % "elasticsearch" % "8.14.0",
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % "8.5.2",
      
      // OAuth
      "oauth.signpost" % "signpost-commonshttp4" % "1.2.1.2",
      
      // Utilities
      "cglib" % "cglib" % "3.3.0",
      "com.sun.activation" % "jakarta.activation" % "1.2.2",
      "com.nulab-inc" % "zxcvbn" % "1.9.0",
      
      // Testing - temporarily disabled due to version incompatibility
      // "org.scalatest" %% "scalatest" % "2.2.6" % Test,
      
      // Jackson
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.12.7.1",
      
      // Flexmark (markdown processing)
      "com.vladsch.flexmark" % "flexmark-profile-pegdown" % "0.40.8",
      "com.vladsch.flexmark" % "flexmark-util-options" % "0.64.0",
      
      // Connection pool
      "com.zaxxer" % "HikariCP" % "4.0.3",
      
      // Test dependencies
      "junit" % "junit" % "4.13.2" % Test,
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
      "org.seleniumhq.selenium" % "htmlunit-driver" % "2.36.0" % Test,
      "org.testcontainers" % "rabbitmq" % "1.20.3" % Test
    )
  )
