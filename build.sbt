enablePlugins(PackPlugin)

name := "VotBot"
version := "0.2"
scalaVersion := "2.13.1"
scalacOptions += "-Ymacro-annotations"

lazy val zioVersion    = "1.0.7"
lazy val zioNioVersion = "1.0.0-RC10"

packResourceDir += (baseDirectory.value / "src" / "main" / "resources" -> "")
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".conf")) }
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".txt")) }
resolvers +=
  "Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots")
libraryDependencies ++= Seq(
  "dev.zio"                      %% "zio"        % zioVersion,
  "dev.zio"                      %% "zio-nio"    % zioNioVersion,
  "com.github.pureconfig"        %% "pureconfig" % "0.12.3",
  "com.beachape"                 %% "enumeratum" % "1.5.15",
  "org.xerial"                   % "sqlite-jdbc" % "3.30.1",
  "com.typesafe.slick"           %% "slick"      % "3.3.2",
  "org.slf4j"                    % "slf4j-nop"   % "1.6.4",
  "com.softwaremill.sttp.client" %% "core"       % "2.0.0",
  "org.jsoup"                    % "jsoup"       % "1.13.1",
  // "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.0.0-RC5",
  "dev.zio" %% "zio-test"     % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
