enablePlugins(PackPlugin)

name := "VotBot"
version := "0.2"
scalaVersion := "2.13.1"
lazy val zioVersion = "1.0.0-RC17"
packResourceDir += (baseDirectory.value / "src" / "main" / "resources" -> "")
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".conf")) }
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".txt")) }

libraryDependencies ++= Seq(
  "dev.zio"                      %% "zio"        % zioVersion,
  "dev.zio"                      %% "zio-nio"    % "0.4.0",
  "com.github.pureconfig"        %% "pureconfig" % "0.11.1",
  "com.beachape"                 %% "enumeratum" % "1.5.13",
  "org.xerial"                   % "sqlite-jdbc" % "3.28.0",
  "com.typesafe.slick"           %% "slick"      % "3.3.2",
  "org.slf4j"                    % "slf4j-nop"   % "1.6.4",
  "com.softwaremill.sttp.client" %% "core"       % "2.0.0-RC5",
  "org.jsoup"                    % "jsoup"       % "1.12.1",
  // "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.0.0-RC5",
  "dev.zio" %% "zio-test"     % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
