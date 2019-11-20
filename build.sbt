enablePlugins(PackPlugin)

name := "VotBot"
version := "0.1"
scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "dev.zio"               %% "zio"        % "1.0.0-RC16",
  "dev.zio"               %% "zio-nio"    % "0.3.1",
  "com.github.pureconfig" %% "pureconfig" % "0.11.1",
  "com.beachape"          %% "enumeratum" % "1.5.13",
  "io.getquill"           %% "quill-sql"  % "3.4.10",
  "org.xerial"            % "sqlite-jdbc" % "3.28.0",
  "io.getquill"           %% "quill-jdbc" % "3.4.10"
)
