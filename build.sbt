enablePlugins(PackPlugin)

name := "VotBot"
version := "0.2"
scalaVersion := "2.13.6"
scalacOptions += "-Ymacro-annotations"

lazy val zioVersion    = "2.0.0-RC4"
lazy val zioNioVersion = "2.0.0-RC5"

packResourceDir += (baseDirectory.value / "src" / "main" / "resources" -> "")
Compile / packageBin / mappings ~= { _.filter(!_._1.getName.endsWith(".conf")) }
Compile / packageBin / mappings ~= { _.filter(!_._1.getName.endsWith(".txt")) }
resolvers +=
  "Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots")
libraryDependencies ++= Seq(
  "dev.zio"                      %% "zio"        % zioVersion,
  "dev.zio"                      %% "zio-nio"    % zioNioVersion,
  "com.github.pureconfig"        %% "pureconfig" % "0.17.1",
  "com.beachape"                 %% "enumeratum" % "1.7.0",
  "org.xerial"                   % "sqlite-jdbc" % "3.36.0.3",
  "com.typesafe.slick"           %% "slick"      % "3.3.3",
  "org.slf4j"                    % "slf4j-nop"   % "1.7.36",
  "com.softwaremill.sttp.client" %% "core"       % "2.3.0",
  "org.jsoup"                    % "jsoup"       % "1.14.3",
  // "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.0.0-RC5",
  "dev.zio" %% "zio-test"     % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
