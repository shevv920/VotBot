enablePlugins(PackPlugin)

name := "VotBot"
version := "0.1"
scalaVersion := "2.12.10"
lazy val zioVersion = "1.0.0-RC17"
packResourceDir += (baseDirectory.value / "src" / "main" / "resources" -> "")
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".conf")) }
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith(".txt")) }

libraryDependencies ++= Seq(
  "dev.zio"               %% "zio"          % zioVersion,
  "dev.zio"               %% "zio-nio"      % "0.4.0",
  "com.github.pureconfig" %% "pureconfig"   % "0.11.1",
  "com.beachape"          %% "enumeratum"   % "1.5.13",
  "io.getquill"           %% "quill-sql"    % "3.4.10",
  "org.xerial"            % "sqlite-jdbc"   % "3.28.0",
  "io.getquill"           %% "quill-jdbc"   % "3.4.10",
  "dev.zio"               %% "zio-test"     % zioVersion % "test",
  "dev.zio"               %% "zio-test-sbt" % zioVersion % "test"
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
