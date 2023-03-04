package com.github.condaincubator.condaenvbuilder.tools

import cats.syntax.either._
import com.fulcrumgenomics.commons.CommonsDef.{DirPath, SafelyClosable}
import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.sopt.{arg, clp}
import com.github.condaincubator.condaenvbuilder.CondaEnvironmentBuilderDef._
import com.github.condaincubator.condaenvbuilder.api.CondaStep.{Channel, Platform}
import com.github.condaincubator.condaenvbuilder.api._
import com.github.condaincubator.condaenvbuilder.cmdline.{ClpGroups, CondaEnvironmentBuilderTool}
import com.github.condaincubator.condaenvbuilder.io.{CondaBuildWriter, SpecParser, SpecWriter}
import com.github.condaincubator.condaenvbuilder.util.Process
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor, yaml}

import scala.sys.process._

@clp(description =
  """
    |Solves a YAML configuration file.
    |
    |For each environment:
    |1. Writes a conda environment YAML with conda and pip requirements.
    |2. Builds a temporary conda environment given the specification from (1) with `conda env create`.
    |3. Exports the conda environment from (2) with `conda env export`
    |4. Updates the package requirements.  This likely adds packages.
    |
    |The output YAML configuration file is platform specific.  Use the `--no-builds` option to make it platform
    |agnostic.
    |""",
  group=ClpGroups.Util)
class Solve
( @arg(flag='c', doc="Input YAML configuration file.") val config: PathToYaml,
  @arg(flag='o', doc="Output YAML configuration file.") val output: PathToYaml = Io.StdOut,
  @arg(flag='n', doc="Assemble environments with the given name(s).", minElements=0) val names: Set[String] = Set.empty,
  @arg(flag='g', doc="Assemble environments with the given group(s).", minElements=0) val groups: Set[String] = Set.empty,
  @arg(doc="Compile the YAML configuration file before solving.") compile: Boolean = true,
  @arg(doc="Remove build specification from dependencies (i.e. use `conda env export --no-builds`)") noBuilds: Boolean = false,
  private[tools] val dryRun: Boolean = false // for testing
) extends CondaEnvironmentBuilderTool {

  Io.assertReadable(config)
  Io.assertCanWriteFile(output)

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

    val tmpDir              = Io.makeTempDir(s"tmp.${config.getFileName}")
    val assemblyOutputDir   = tmpDir.resolve("environments")
    val condaEnvironmentDir = tmpDir.resolve("conda")

    Io.mkdirs(assemblyOutputDir)
    Io.mkdirs(condaEnvironmentDir)

    logger.info(f"Solving ${environments.length}%,d out of ${spec.specs.length}%,d environments.")
    val newEnvironments = environments.zipWithIndex.map { case (environment, index) =>
      logger.info(f"Solving environment (${index+1}/${environments.length}): ${environment.name}")

      require(environment.steps.collect { case step: CondaStep => step }.length <= 1, "Found more than one conda step")
      require(environment.steps.collect { case step: PipStep => step }.length <= 1, "Found more than one pip step")

      // Set up where files and conda environments will get written
      val condaEnvironmentPrefix: DirPath = condaEnvironmentDir.resolve(environment.name)
      val environmentYaml: PathToYaml     = Io.makeTempFile("config.", f".${environment.name}.${CondaEnvironmentBuilderTool.YamlFileExtension}")
      val writer                          = CondaBuildWriter(
        environment               = environment,
        output                    = assemblyOutputDir,
        environmentYaml           = Some(environmentYaml),
        condaEnvironmentDirectory = Some(condaEnvironmentDir)
      )

      // Write the environment YAML
      writer.writeEnvironmentYaml(logger=logger)

      val exportedYaml: PathToYaml = if (dryRun) writer.environmentYaml else {
        // Build the environment
        logger.info(s"Building a temporary conda environment for ${environment.name} to: $condaEnvironmentPrefix")
        Process.run(
          logger=logger,
          f"$condaExecutable env create --verbose --quiet --prefix $condaEnvironmentPrefix --file $environmentYaml"
        )

        // Export the environment
        logger.info(s"Exporting the conda environment for ${environment.name}")
        val exportedYaml: PathToYaml     = Io.makeTempFile("config.", f".${environment.name}.${CondaEnvironmentBuilderTool.YamlFileExtension}")
        val condaEnvExportArgs: String = if (noBuilds) "--no-builds" else ""
        Process.run(
          logger=logger,
          f"$condaExecutable env export --prefix $condaEnvironmentPrefix $condaEnvExportArgs"
            #| """egrep -v "^prefix""""
            #| f"""sed "s/name: null/name: ${environment.name}/""""
            #> exportedYaml.toFile
        )

        // Remove the temporary environment
        logger.info(s"Removing the temporary the conda environment for ${environment.name}")
        Process.run(logger=logger, s"rm -rv $condaEnvironmentPrefix")

        exportedYaml
      }

      // Read in the exported environment
      logger.info(s"Reading the exported environment from: $exportedYaml")
      val condaEnvironment = {
        implicit val decoder: Decoder[CondaEnvironment] = CondaEnvironment.decoder
        val reader = Io.toReader(exportedYaml)
        val json = yaml.parser.parse(reader)
        reader.safelyClose()
        json.flatMap(_.as[CondaEnvironment]).valueOr(throw _)
      }

      // Merge it with the current spec
      logger.info(s"Updating the environment")
      require(environment.name == condaEnvironment.name, "Bug: names mismatch")
      val conda: Option[CondaStep] = environment.steps.collectFirst { case step: CondaStep =>
        logger.debug(f"Found existing conda step with ${step.requirements.length}%,d requirements")
        // Require all existing packages to be found in the solved environment
        step.requirements.foreach { req =>
          require(condaEnvironment.conda.exists(_.name == req.name), s"Could not find conda package '${req.name}' in solved conda packages")
        }
        logger.debug(f"Updating to ${condaEnvironment.conda.length}%,d requirements")
        step.copy(requirements=condaEnvironment.conda)
      }
      val pip: Option[PipStep] = environment.steps.collectFirst { case step: PipStep =>
        logger.debug(f"Found existing pip step with ${step.requirements.length}%,d requirements")
        // Require all existing packages to be found in the solved environment
        step.requirements.foreach { req =>
          require(condaEnvironment.pip.exists(_.name == req.name), s"Could not find pip package '${req.name}' in solved pip packages")
        }
        logger.debug(f"Updating to ${condaEnvironment.pip.length}%,d requirements")
        step.copy(requirements=condaEnvironment.pip)
      }
      val code: Seq[CodeStep] = environment.steps.collect { case step: CodeStep => step }
      logger.info(f"Solved environment (${index+1}/${environments.length}): ${environment.name}")
      environment.copy(steps=conda.toSeq ++ pip.toSeq ++ code)
    }

    // Write out the environment
    val newSpecs = spec.specs.map { envSpec =>
      newEnvironments.find(_.name == envSpec.environment.name) match {
        case None                 =>
          require(environments.forall(_.name != envSpec.environment.name), s"Bug: ${envSpec.environment.name}")
          envSpec
        case Some(newEnvironment) =>
          require(environments.exists(_.name == envSpec.environment.name), s"Bug: ${envSpec.environment.name}")
          envSpec.copy(environment=newEnvironment)
      }
    }
    val newSpec = spec.copy(specs=newSpecs)
    SpecWriter.write(spec=newSpec, config=output)
  }
}

private case class CondaEnvironment
(name: String,
 platforms: Seq[Platform],
 channels: Seq[Channel],
 conda: Seq[Requirement],
 pip: Seq[Requirement]
)

private object CondaEnvironment {

  import com.github.condaincubator.condaenvbuilder.api.Decoders.DecodeRequirement

  def decoder: Decoder[CondaEnvironment] = new Decoder[CondaEnvironment] {
    final def apply(c: HCursor): Decoder.Result[CondaEnvironment] = {
      // Get the keys at this level
      val keys            = c.keys.map(_.toSeq).getOrElse(Seq.empty)
      val nameResult      = c.downField("name").as[String]
      val platformsResult = if (keys.contains("platforms")) c.downField("platforms").as[Seq[String]] else Right(Seq.empty[String])
      val channelsResult  = if (keys.contains("channels")) c.downField("channels").as[Seq[String]] else Right(Seq.empty[String])
      val dependencies    = c.downField("dependencies").values.toSeq.flatten
      val condaResult: Result[Seq[Requirement]] = {
        val results = dependencies.filterNot(_.isObject).map(_.as[Requirement])
        results.collectFirst { case left: Left[DecodingFailure, Requirement] => left } match {
          case Some(Left(left)) => Left[DecodingFailure, Seq[Requirement]](left)
          case None             => Right(results.collect { case Right(requirement) => requirement })
        }
      }
      val pipResult: Result[Seq[Requirement]]      = dependencies.find(_.isObject) match {
        case None       => Right(Seq.empty[Requirement])
        case Some(json) => json.hcursor.downField("pip").as[Seq[Requirement]]
      }

      for {
        name <- nameResult
        platforms <- platformsResult
        channels <- channelsResult
        conda <- condaResult
        pip <- pipResult
      } yield {
        CondaEnvironment(
          name      = name,
          platforms = platforms,
          channels  = channels,
          conda     = conda,
          pip       = pip
        )
      }
    }
  }
}
