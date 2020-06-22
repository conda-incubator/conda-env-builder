package com.github.nh13.condaenvbuilder.tools


import java.nio.file.Files

import com.fulcrumgenomics.commons.CommonsDef.DirPath
import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.sopt.{arg, clp}
import com.github.nh13.condaenvbuilder.CondaEnvironmentBuilderDef._
import com.github.nh13.condaenvbuilder.api.{Environment, Spec}
import com.github.nh13.condaenvbuilder.cmdline.{ClpGroups, CondaEnvironmentBuilderTool}
import com.github.nh13.condaenvbuilder.io.{BuildWriter, SpecParser}


@clp(description =
  """
    |Assembles a YAML Configuration file.
    |
    |For each environment, writes:
    |1. the conda environment YAML to `<output>/<env-name>.yml`
    |2. the conda environment build script to `<output>/<env-name>.build-conda.sh`
    |3. the custom code build script to `<output>/<env-name>.build-local.sh`
    |
    |The directory in which conda environment(s) are created can be specified with the `--prefix` option.
    |""",
  group=ClpGroups.Util)
class Assemble
( @arg(flag='c', doc="Input YAML configuration file.") val config: PathToYaml,
  @arg(flag='o', doc="Output directory.") val output: DirPath,
  @arg(flag='p', doc="The conda path prefix in which to store conda environments.") val prefix: Option[DirPath] = None,
  @arg(flag='f', doc="Overwrite existing files.") val overwrite: Boolean = false,
  @arg(flag='n', doc="Assemble environments with the given name(s).", minElements=0) val names: Set[String] = Set.empty,
  @arg(flag='g', doc="Assemble environments with the given group(s).", minElements=0) val groups: Set[String] = Set.empty,
  @arg(doc="Compile the YAML configuration file before assembling.") compile: Boolean = true
) extends CondaEnvironmentBuilderTool {

  Io.assertReadable(config)

  override def execute(): Unit = {
    logger.info(s"Loading configuration from: $config")
    val spec: Spec = {
      if (this.compile) SpecParser(config).compiled
      else SpecParser(config).compiled
    }

    val environments: Seq[Environment] = spec
      .specs
      .map(_.environment)
      .filter(e => names.isEmpty || names.contains(e.name))
      .filter(e => groups.isEmpty || groups.contains(e.group))

    logger.info(f"Building ${environments.length}%,d out of ${spec.specs.length}%,d environments.")
    spec.specs.map(_.environment).zipWithIndex.map { case (environment, index) =>
      val writer = BuildWriter(environment=environment, output=output, condaEnvironmentDirectory=prefix)
      if (!overwrite) {
        logger.info(f"Checking environment (${index+1}/${environments.length}): ${environment.name}")
        writer.allOutputs.foreach { path =>
          require(!path.toFile.exists(), s"Path exists, but --overwrite is not set: $path")
        }
      }
      writer
    }.zipWithIndex.foreach { case (writer, index)  =>
      val environment: Environment = writer.environment
      logger.info(f"Building environment (${index+1}/${environments.length}): ${environment.name}")

      // Build the output directory
      if (!output.toFile.exists) Files.createDirectory(output)

      // Write the build files
      writer.write(logger=this.logger)
    }
  }
}
