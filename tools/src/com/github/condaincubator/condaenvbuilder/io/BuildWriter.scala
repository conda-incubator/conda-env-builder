package com.github.condaincubator.condaenvbuilder.io

import com.fulcrumgenomics.commons.CommonsDef.{DirPath, FilePath}
import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.commons.util.{LazyLogging, Logger}
import com.github.condaincubator.condaenvbuilder.CondaEnvironmentBuilderDef.PathToYaml
import com.github.condaincubator.condaenvbuilder.api.CodeStep.Command
import com.github.condaincubator.condaenvbuilder.api.CondaStep.Platform
import com.github.condaincubator.condaenvbuilder.api.{CodeStep, CondaStep, Environment, PipStep}
import com.github.condaincubator.condaenvbuilder.cmdline.CondaEnvironmentBuilderTool

import java.io.PrintWriter
import java.nio.file.Paths

trait BuildWriterConstants {

  /** Returns the path to the environment's conda YAML. */
  protected def toEnvironmentYaml(environment: Environment, output: DirPath): PathToYaml = {
    output.resolve(f"${environment.name}.${CondaEnvironmentBuilderTool.YamlFileExtension}")
  }

  /** Returns the path to the environment's conda LOCK file. */
  protected def toEnvironmentLockYaml(environment: Environment, platform: Platform, output: DirPath): PathToYaml = {
    output.resolve(f"${environment.name}.${platform}.conda-lock.${CondaEnvironmentBuilderTool.YamlFileExtension}")
  }


  /** Returns the path to the environment's conda build script. */
  protected def toCondaBuildScript(environment: Environment, output: DirPath): FilePath = {
    output.resolve(f"${environment.name}.build-conda.sh")
  }

  /** Returns the path to the environment's custom code build script. */
  protected def toCodeBuildScript(environment: Environment, output: DirPath): FilePath = {
    output.resolve(f"${environment.name}.build-local.sh")
  }
}


trait BuildWriter extends LazyLogging {
  def environment: Environment

  /** the path to use for the environment's conda YAML */
  def environmentYaml: PathToYaml

  /** The path to use for the environment's conda build script */
  def condaBuildScript: FilePath

  /** The path to use for the environment's custom code build script */
  def codeBuildScript: FilePath

  /** The directory in which conda environments should be stored when created */
  def condaEnvironmentDirectory: Option[DirPath]

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
    logger.info(s"Writing the conda environment YAML for ${environment.name} to: $environmentYaml")

    val condaStep: Option[CondaStep] = environment.steps.collect { case step: CondaStep => step } match {
      case Seq() => None
      case Seq(step) => Some(step)
      case steps => throw new IllegalArgumentException(
        s"Expected a single conda step, found ${steps.length} conda steps.  Did you forget to compile?"
      )
    }

    val pipStep: Option[PipStep] = environment.steps.collect { case step: PipStep => step } match {
      case Seq() => None
      case Seq(step) => Some(step)
      case steps => throw new IllegalArgumentException(
        s"Expected a single pip step, found ${steps.length} pip steps.  Did you forget to compile?"
      )
    }

    val writer = new PrintWriter(Io.toWriter(environmentYaml))
    writer.println(f"name: ${environment.name}")
    condaStep.foreach { step =>
      if (step.platforms.nonEmpty) {
        writer.println("platforms:")
        step.platforms.foreach { platform => writer.println(f"  - $platform") }
      }
    }
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

  /** Write the conda build command. */
  protected def writeCondaBuildCommand(writer: PrintWriter): Unit

  /** Writes the conda build script. */
  private def writeCondaBuildScript(logger: Logger = this.logger): Unit = {
    logger.info(s"Writing conda build script for ${environment.name} to: $condaBuildScript")
    val writer = new PrintWriter(Io.toWriter(condaBuildScript))
    writer.println("#/bin/bash\n")
    writer.println(f"# Conda build file for environment: ${environment.name}")
    writer.println("set -xeuo pipefail\n")
    writer.println("# Move to the scripts directory")
    writer.println("pushd $(dirname $0)\n")
    writer.println("# Build the conda environment")
    this.writeCondaBuildCommand(writer=writer)
    writer.println("popd\n")
    writer.close()
  }

  /** Writes the custom code build script. */
  protected def writeCodeBuildScript(logger: Logger = this.logger): Unit = {
    logger.info(s"Writing custom code build script for ${environment.name} to: $codeBuildScript")

    val codeStep: Option[CodeStep] = environment.steps.collect { case step: CodeStep => step } match {
      case Seq() => None
      case Seq(step) => Some(step)
      case steps => throw new IllegalArgumentException(
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
      case None => writer.println("# No custom commands")
      case Some(step) =>
        writer.println(f"# Activate conda environment: ${environment.name}")
        writer.println("set +eu") // because of unbound variables
        writer.println("PS1=dummy\n") // for sourcing
        writer.println(f". $$(conda info --base | tail -n 1)/etc/profile.d/conda.sh") // tail to ignore mamba header
        writer.println(f"conda activate ${environment.name}")
        writer.println()
        writer.println("set -eu") //
        writer.println(f"pushd $${repo_root}")
        step.commands.foreach { command: Command =>
          writer.println(f"$command")
        }
        writer.println("popd\n\n")
    }
    writer.close()
  }
}
