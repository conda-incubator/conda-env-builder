package com.github.condaincubator.condaenvbuilder.io

import com.fulcrumgenomics.commons.CommonsDef.{DirPath, FilePath}
import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.commons.util.Logger
import com.github.condaincubator.condaenvbuilder.CondaEnvironmentBuilderDef.PathToYaml
import com.github.condaincubator.condaenvbuilder.api.CondaStep.Platform
import com.github.condaincubator.condaenvbuilder.api.Environment
import com.github.condaincubator.condaenvbuilder.cmdline.CondaEnvironmentBuilderTool
import com.github.condaincubator.condaenvbuilder.util.Process

import java.io.PrintWriter

/** Companion to [[CondaLockInstallWriter]].  */
object CondaLockInstallWriter extends BuildWriterConstants {

  /** Builds a new [[CondaLockInstallWriter]] for the given environment.
   *
   * @param environment the environment for which build files should be created.
   * @param output the output directory where build files should be created.
   * @param platform the platform on which to install
   * @param environmentYaml the path to use for the environment's conda YAML, otherwise `<output>/<env-name>.yml`.
   * @param environmentLockYaml the path to use for the environment's conda YAML, otherwise `<output>/<env-name>.<platform>.conda-lock.yml`.
   * @param condaBuildScript the path to use for the environment's conda build script,
   *                         otherwise `<output>/<env-name>.build-conda.sh`.
   * @param codeBuildScript the path to use for the environment's custom code build script,
   *                        otherwise `<output>/<env-name>.build-local.sh`.
   * @param condaEnvironmentDirectory the directory in which conda environments should be stored when created.
   * @return
   */
  def apply(environment: Environment,
            output: DirPath,
            platform: Platform,
            environmentYaml: Option[PathToYaml] = None,
            environmentLockYaml: Option[PathToYaml] = None,
            condaBuildScript: Option[FilePath] = None,
            codeBuildScript: Option[FilePath] = None,
            condaEnvironmentDirectory: Option[DirPath] = None): CondaLockInstallWriter = {
    new CondaLockInstallWriter(
      environment               = environment,
      platform                  = platform,
      environmentYaml           = environmentYaml.getOrElse(toEnvironmentYaml(environment, output)),
      environmentLockYaml       = environmentLockYaml.getOrElse(toEnvironmentLockYaml(environment, platform, output)),
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
 * @param platform the platform on which to install
 * @param environmentYaml the path to use for the environment's conda YAML.
 * @param environmentLockYaml the path to use for the environment's conda LOCK file.
 * @param condaBuildScript the path to use for the environment's conda build script
 * @param codeBuildScript the path to use for the environment's custom code build script
 * @param condaEnvironmentDirectory the directory in which conda environments should be stored when created.
 */
case class CondaLockInstallWriter(environment: Environment,
                                  environmentYaml: PathToYaml,
                                  platform: Platform,
                                  environmentLockYaml: PathToYaml,
                                  condaBuildScript: FilePath,
                                  codeBuildScript: FilePath,
                                  condaEnvironmentDirectory: Option[DirPath]) extends BuildWriter {

  private lazy val condaLockExecutable: String = "conda-lock"

  /** Returns all the output files that will be written by this writer */
  override def allOutputs: Iterable[FilePath] = super.allOutputs ++ Iterator(environmentLockYaml)

  /** Writes the conda environment file and all the build scripts. */
  override def write(logger: Logger = this.logger): Unit = {
    super.write(logger=logger)

    // Write the lock file
    this.writeEnvironmentLock(logger=this.logger)
  }

  /** Writes the conda build command. */
  protected def writeCondaBuildCommand(writer: PrintWriter): Unit = {
    writer.write(f"$condaLockExecutable install")
    if (CondaEnvironmentBuilderTool.UseMamba) writer.write(" --mamba")
    condaEnvironmentDirectory match {
      case Some(pre) => writer.write(f" --prefix ${pre.toAbsolutePath}/${environment.name}")
      case None => writer.write(f" --name ${environment.name}")
    }
    writer.println(f" ${environmentLockYaml.toFile.getName}\n")
  }

  private def writeEnvironmentLock(logger: Logger = this.logger): Unit = {
    // Export the environment
    logger.info(s"Locking ${environment.name} for $platform with conda-lock")
    val condaLockOptionalArgs: String = if (CondaEnvironmentBuilderTool.UseMamba) "--mamba" else ""
    Process.run(
      logger = logger,
      f"$condaLockExecutable lock $condaLockOptionalArgs" +
        f" --platform $platform" +
        f" --file $environmentYaml" +
        f" --kind lock" +
        f" --lockfile $environmentLockYaml"
    )
  }
}
