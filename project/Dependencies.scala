import sbt._

object Dependencies {

  lazy val kindProjector = ("org.typelevel" % "kind-projector"     % "0.13.3").cross(CrossVersion.full)
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"

  lazy val `trace4cats-avro` =
    Seq("io.janstenpickle" %% "trace4cats-kernel" % "0.14.0", "com.github.fd4s" %% "vulcan" % "1.11.1") ++ Seq(
      "io.janstenpickle" %% "trace4cats-testkit" % "0.14.0",
      "com.github.fd4s"  %% "vulcan-generic"     % "1.11.1",
      "org.slf4j"         % "slf4j-nop"          % "1.7.36"
    ).map(_ % Test)

  lazy val `avro-test`: Seq[ModuleID] =
    Seq("io.janstenpickle" %% "trace4cats-testkit" % "0.14.0", "ch.qos.logback" % "logback-classic" % "1.2.13")

  lazy val `avro-exporter` = Seq("io.janstenpickle" %% "trace4cats-core" % "0.14.0", "co.fs2" %% "fs2-io" % "3.2.14")
  lazy val `avro-server` = Seq("org.typelevel" %% "log4cats-slf4j" % "2.4.0", "co.fs2" %% "fs2-io" % "3.2.14")

}
