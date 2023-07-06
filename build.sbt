// See README.md for license details.

ThisBuild / scalaVersion := "2.13.8"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "hitsz"

val chiselVersion = "3.5.4"
lazy val commonSettings = Seq(
    name := "MiniRV",
    libraryDependencies ++= Seq(
        "edu.berkeley.cs" %% "chisel3" % chiselVersion,
        "edu.berkeley.cs" %% "chiseltest" % "0.5.4" % "test"
    ),
    scalacOptions ++= Seq(
        "-language:reflectiveCalls",
        "-deprecation",
        "-feature",
        "-Xcheckinit",
        "-P:chiselplugin:genBundleElements",
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),
)


lazy val root = (project in file("core"))
    .dependsOn(macroSub)
    .settings(commonSettings)

lazy val macroSub = (project in file("macro"))
    .settings(
        commonSettings,
        scalacOptions ++= Seq(
            "-Ymacro-annotations"
        ),
        libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )

mainClass in (Compile, run) := Some("top.origami404.miniRV.utils.Main")