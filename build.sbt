ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "dynamic-logback",
      libraryDependencies += "com.typesafe" % "config" % "1.4.2",
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11"
  )
