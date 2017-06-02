lazy val defaults = Seq(
  organization := "com.hellosoda",
  version      := "1.0.0-SNAPSHOT",
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq("2.11.11", "2.12.2"),
  scalacOptions in Compile ++= Seq(
    "-encoding", "utf-8",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings",
    "-Ywarn-unused-import",
    "-Ydelambdafy:method",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-target:jvm-1.8"),
  testOptions in Test += Tests.Argument("-oDF"))

lazy val dependencies = libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "4.1.0"
    exclude("ch.qos.logback", "logback-classic"),
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "org.apache.logging.log4j" % "log4j-api"        % "2.6.1" % "test",
  "org.apache.logging.log4j" % "log4j-core"       % "2.6.1" % "test",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.6.1" % "test",
  "org.scalatest" %% "scalatest" % "3.0.2" % "test")

lazy val parent =
  (project in file(".")).
  settings(defaults: _*).
  settings(
    publish      := {},
    publishLocal := {}).
  aggregate(core).
  aggregate(`play-json`)

lazy val core =
  (project in file("core")).
  settings(defaults: _*).
  settings(dependencies: _*)

lazy val `play-json` =
  (project in file("play-json")).
  settings(defaults: _*).
  settings(libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % "2.4.11")).
  dependsOn(core).
  aggregate(core)
