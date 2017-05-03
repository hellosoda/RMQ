lazy val defaults = Seq(
  organization := "com.hellosoda",
  version      := "1.0.0-SNAPSHOT",
  scalaVersion := "2.11.11",
  scalacOptions in Compile ++= Seq(
    "-encoding", "utf-8",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings",
    "-Ydelambdafy:method",
    "-target:jvm-1.8"))

lazy val dependencies = libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "4.1.0").
  map { _.exclude("ch.qos.logback", "logback-classic") }

lazy val root =
  (project in file(".")).
  settings(defaults: _*).
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
    "com.typesafe.play" %% "play-json" % "2.4.0")).
  dependsOn(core).
  aggregate(core)
