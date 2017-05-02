lazy val defaults = Seq(
  organization := "com.hellosoda",
  version      := "1.0.0-SNAPSHOT",
  scalaVersion := "2.11.11")

lazy val deps = libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "4.1.0").
  map { _.exclude("ch.qos.logback", "logback-classic") }

scalacOptions in Compile ++= Seq(
  "-encoding", "utf-8",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Xfatal-warnings",
  "-Ydelambdafy:method",
  "-target:jvm-1.8")

lazy val root =
  (project in file(".")).
  settings(defaults: _*).
  aggregate(core)

lazy val core =
  (project in file("core")).
  settings(defaults: _*).
  settings(deps: _*)
