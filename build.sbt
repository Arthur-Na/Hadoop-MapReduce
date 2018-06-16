version := "0.1"
scalaVersion := "2.12.6"


lazy val master = (project in file("master")).
  settings(settings).
  settings(
    name := "master",
    assemblySettings,
    mainClass in Compile := Some("Master")
  )

lazy val slave = (project in file("slave")).
  settings(settings).
  settings(
    assemblySettings,
    name := "slave",
    mainClass in Compile := Some("Slave")
  )

lazy val deploy = (project in file("deploy")).
  settings(settings).
  settings(
    assemblySettings,
    name := "deploy",
    mainClass in Compile := Some("Deploy")
  )


lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
  }
)

lazy val settings =
  commonSettings ++
  wartremoverSettings ++
  scalafmtSettings

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )
)

lazy val wartremoverSettings = Seq(
  wartremoverWarnings in (Compile, compile) ++= Warts.allBut(Wart.Throw)
)

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
    scalafmtTestOnCompile := true,
    scalafmtVersion := "1.2.0"
  )
