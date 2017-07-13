lazy val defaults = Seq(
  organization := "com.hellosoda.rmq",
  version      := getVersion(),
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq("2.11.11"),
  scalacOptions in Compile ++= Seq(
    "-encoding", "utf-8",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings",
    "-Ywarn-unused-import",
    "-Ydelambdafy:method",
    "-Yno-adapted-args",
    "-Ywarn-dead-code"),
  testOptions in Test += Tests.Argument("-oDF"))

lazy val dependencies = libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "4.1.1"
    exclude("ch.qos.logback", "logback-classic"),
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "org.apache.logging.log4j" % "log4j-api"        % "2.6.1" % "test",
  "org.apache.logging.log4j" % "log4j-core"       % "2.6.1" % "test",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.6.1" % "test",
  "org.scalatest" %% "scalatest" % "3.0.2" % "test")

lazy val publishing = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  useGpg := true,
  licenses := Seq(
    "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  homepage := Some(url("https://github.com/hellosoda/RMQ")),
  scmInfo := Some(ScmInfo(
    url("https://github.com/hellosoda/RMQ"),
    "scm:git@github.com:hellosoda/RMQ.git")),
  pgpSigningKey := Some(3400552037L),
  publishTo := Some(sonatypeDefaultResolver.value))

//

lazy val parent =
  (project in file(".")).
  settings(defaults: _*).
  settings(
    name         := "rmq-parent",
    publish      := {},
    publishLocal := {}).
  aggregate(core).
  aggregate(`play-json`)

lazy val benchmark =
  (project in file("benchmark")).
  settings(defaults: _*).
  settings(libraryDependencies ++= Seq(
    "com.github.scopt" %% "scopt" % "3.6.0")).
  settings(
    name         := "rmq-benchmark",
    publish      := {},
    publishLocal := {}).
  dependsOn(core)

lazy val core =
  (project in file("core")).
  settings(defaults: _*).
  settings(dependencies: _*).
  settings(publishing: _*).
  settings(
    name := "rmq-core")

lazy val `play-json` =
  (project in file("play-json")).
  settings(defaults: _*).
  settings(publishing: _*).
  settings(libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % "2.4.11")).
  settings(
    name := "rmq-play-json").
  dependsOn(core).
  aggregate(core)

//

def getVersion () : String = {
  val base = ("git describe --tags --always" !!).trim
  val hashOnly = "(^[0-9a-z]$)".r
  val offset   = "^(.*?)\\-[0-9]+\\-[0-9a-z]+".r

  ("git describe --tags --always" !!).trim match {
    case hashOnly(hash) => hash
    case offset(version) => s"$version-SNAPSHOT"
    case other => other
  }
}
