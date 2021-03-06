logLevel := Level.Warn

// fetching
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.2")
// fix https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"

// benchmark
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.2")

// cross build
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "0.6.23")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")
addSbtPlugin("com.vmunier"        % "sbt-web-scalajs"          % "1.0.8-0.6")
addSbtPlugin("ch.epfl.scala"      % "sbt-web-scalajs-bundler"  % "0.13.1")
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager"      % "1.3.6")
addSbtPlugin("com.typesafe.sbt"   % "sbt-gzip"                 % "1.0.2")
addSbtPlugin("com.typesafe.sbt"   % "sbt-digest"               % "1.1.3")

// ci
addSbtPlugin("com.dwijnand"      % "sbt-travisci"    % "1.1.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"         % "0.9.3")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"         % "1.1.1")
addSbtPlugin("org.tpolecat"      % "tut-plugin"      % "0.6.7")
addSbtPlugin("com.47deg"         % "sbt-microsites"  % "0.9.1")
addSbtPlugin("com.github.gseitz" % "sbt-release"     % "1.0.8")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype"    % "2.3")
addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % "0.2.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-ghpages"     % "0.6.2")
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"     % "0.3.4")

// linting
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.3.7")

// test coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

// scalac
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"        % "0.1.6")
addSbtPlugin("org.lyranthe.sbt"          % "partial-unification" % "1.1.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")