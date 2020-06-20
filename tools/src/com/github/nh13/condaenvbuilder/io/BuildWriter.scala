package com.github.nh13.condaenvbuilder.io

import java.io.PrintWriter
import java.nio.file.Paths

import com.fulcrumgenomics.commons.CommonsDef.{DirPath, FilePath}
import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.commons.util.{LazyLogging, Logger}
import com.github.nh13.condaenvbuilder.CondaEnvironmentBuilderDef.PathToYaml
import com.github.nh13.condaenvbuilder.api.{CodeStep, CondaStep, Environment, PipStep}
import com.github.nh13.condaenvbuilder.cmdline.CondaEnvironmentBuilderTool

/** Companion to [[BuildWriter]].  */
object BuildWriter {

  /** Returns the path to the environment's conda YAML. */
  private def toEnvironmentYaml(environment: Environment, output: DirPath): PathToYaml = {
    output.resolve(f"${environment.name}.yaml")
  }

  /** Returns the path to the environment's conda build script. */
  private def toCondaBuildScript(environment: Environment, output: DirPath): FilePath = {
    output.resolve(f"${environment.name}.build-conda.sh")
  }

  /** Returns the path to the environment's custom code build script. */
  private def toCodeBuildScript(environment: Environment, output: DirPath): FilePath = {
    output.resolve(f"${environment.name}.build-local.sh")
  }

  /** Builds a new [[BuildWriter]] for the given environment.
    *
    * @param environment the environment for which build files should be created.
    * @param output the output directory where build files should be created.
    * @param environmentYaml the path to use for the environment's conda YAML, otherwise `<output>/<env-name>.yaml`.
    * @param condaBuildScript the path to use for the environment's conda build script,
    *                         otherwise `<output>/<env-name>.build-conda.sh`.
    * @param codeBuildScript the path to use for the environment's custom code build script,
    *                        otherwise `<output>/<env-name>.build-local.sh`.
    * @param condaEnvironmentDirectory the directory in which conda environments should be stored when created.
    * @return
    */
  def apply(environment: Environment,
            output: DirPath,
            environmentYaml: Option[PathToYaml] = None,
            condaBuildScript: Option[FilePath] = None,
            codeBuildScript: Option[FilePath] = None,
            condaEnvironmentDirectory: Option[DirPath] = None): BuildWriter = {
    BuildWriter(
      environment               = environment,
      environmentYaml           = environmentYaml.getOrElse(toEnvironmentYaml(environment, output)),
      condaBuildScript          = condaBuildScript.getOrElse(toCondaBuildScript(environment, output)),
      codeBuildScript           = codeBuildScript.getOrElse(toCodeBuildScript(environment, output)),
      condaEnvironmentDirectory = condaEnvironmentDirectory
    )
  }
}

/** Writer that is used to create the build scripts for the conda environments.
  *
  * The conda build script should be executed first, then the custom code build script.  The conda environment
  * specification is stored in the given environment YAML path.
  *
  * @param environment the environment for which build files should be created.
  * @param environmentYaml the path to use for the environment's conda YAML.
  * @param condaBuildScript the path to use for the environment's conda build script
  * @param codeBuildScript the path to use for the environment's custom code build script
  * @param condaEnvironmentDirectory the directory in which conda environments should be stored when created.
  */
case class BuildWriter(environment: Environment,
                       environmentYaml: PathToYaml,
                       condaBuildScript: FilePath,
                       codeBuildScript: FilePath,
                       condaEnvironmentDirectory: Option[DirPath]) extends LazyLogging {

  private lazy val condaExecutable: String = if (CondaEnvironmentBuilderTool.UseMamba) "mamba" else "conda"

  /** Returns all the output files that will be written by this writer */
  def allOutputs: Iterable[FilePath] = Seq(environmentYaml, condaBuildScript, codeBuildScript)

  /** Writes the conda environment file and all the build scripts. */
  def write(logger: Logger = this.logger): Unit = {
    // Write the environment file
    this.writeEnvironmentYaml(logger=this.logger)
    // Write the conda build script
    this.writeCondaBuildScript(logger=this.logger)
    // Write the local build script
    this.writeCodeBuildScript(logger=this.logger)
  }

  /** Writes the conda environment file. */
  def writeEnvironmentYaml(logger: Logger = this.logger): Unit = {
    logger.info(s"Writing the environment YAML for ${environment.name} to: $environmentYaml")

    val condaStep: Option[CondaStep] = environment.steps.collect { case step: CondaStep => step } match {
      case Seq()     => None
      case Seq(step) => Some(step)
      case steps     => throw new IllegalArgumentException(
        s"Expected a single conda step, found ${steps.length} conda steps.  Did you forget to compile?"
      )
    }

    val pipStep: Option[PipStep] = environment.steps.collect { case step: PipStep => step } match {
      case Seq()     => None
      case Seq(step) => Some(step)
      case steps     => throw new IllegalArgumentException(
        s"Expected a single pip step, found ${steps.length} pip steps.  Did you forget to compile?"
      )
    }

    val writer = new PrintWriter(Io.toWriter(environmentYaml))
    writer.println(f"name: ${environment.name}")
    condaStep.foreach { step =>
      if (step.channels.nonEmpty) {
        writer.println("channels:")
        step.channels.foreach { channel => writer.println(f"  - $channel") }
      }
    }
    if (condaStep.isDefined || pipStep.isDefined) {
      writer.println("dependencies:")
    }
    condaStep.foreach { step =>
      step.requirements.foreach { requirement => writer.println(f"  - $requirement") }
    }
    pipStep.foreach { step =>
      writer.println("  - pip")
      writer.println("  - pip:")
      step.args.foreach { arg => writer.println(f"    - $arg") }
      step.requirements.foreach { requirement => writer.println(f"    - $requirement") }
    }
    condaEnvironmentDirectory.foreach { pre => writer.println(f"prefix: $pre") }
    writer.close()
  }

  /** Writes the conda build script. */
  def writeCondaBuildScript(logger: Logger = this.logger): Unit = {
    logger.info(s"Writing conda build script for ${environment.name} to: $condaBuildScript")
    val writer = new PrintWriter(Io.toWriter(condaBuildScript))
    writer.println("#/bin/bash\n")
    writer.println(f"# Conda build file for environment: ${environment.name}")
    writer.println("set -xeuo pipefail\n")
    writer.println("# Move to the scripts directory")
    writer.println("pushd $(dirname $0)\n")
    writer.println("# Build the conda environment")
    writer.write(f"$condaExecutable env create --force --verbose --quiet")
    condaEnvironmentDirectory match {
      case Some(pre) => writer.write(f" --prefix ${pre.toAbsolutePath}/${environment.name}")
      case None      => writer.write(f" --name ${environment.name}")
    }
    writer.println(f" --file ${environmentYaml.toFile.getName}\n")
    writer.println("popd\n")
    writer.close()
  }

  /** Writes the custom code build script. */
  def writeCodeBuildScript(logger: Logger = this.logger): Unit = {
    logger.info(s"Writing custom code build script for ${environment.name} to: $codeBuildScript")

    val codeStep: Option[CodeStep] = environment.steps.collect { case step: CodeStep => step } match {
      case Seq()     => None
      case Seq(step) => Some(step)
      case steps     => throw new IllegalArgumentException(
        s"Expected a single code step, found ${steps.length} code steps.  Did you forget to compile?"
      )
    }

    val writer = new PrintWriter(Io.toWriter(codeBuildScript))
    writer.println("#/bin/bash")
    writer.println(f"# Custom code build file for environment: ${environment.name}")
    writer.println("set -xeuo pipefail\n")
    val buildPath = codeStep.map(_.path).getOrElse(Paths.get("."))
    writer.println(f"""repo_root=$${1:-"$buildPath"}\n""")
    codeStep match {
      case None       => writer.println("# No custom commands")
      case Some(step) =>
        writer.println(f"# Activate conda environment: ${environment.name}")
        writer.println("set +eu")  // because of unbound variables
        writer.println("PS1=dummy\n")  // for sourcing
        writer.println(f". $$($condaExecutable info --base | tail -n 1)/etc/profile.d/conda.sh") // tail to ignore mamba header
        writer.println(f"$condaExecutable activate ${environment.name}")
        writer.println("set -eu") //
        writer.println(f"pushd $${repo_root}\n")
        codeStep.map(_.commands).foreach { command =>
          writer.println(f"$command")
        }
        writer.println("popd\n\n")
    }
    writer.close()
  }
}