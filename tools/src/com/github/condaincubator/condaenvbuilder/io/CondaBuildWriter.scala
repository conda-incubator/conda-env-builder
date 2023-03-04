package com.github.condaincubator.condaenvbuilder.io

import com.fulcrumgenomics.commons.CommonsDef.{DirPath, FilePath}
import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.commons.util.Logger
import com.github.condaincubator.condaenvbuilder.CondaEnvironmentBuilderDef.PathToYaml
import com.github.condaincubator.condaenvbuilder.api.Environment
import com.github.condaincubator.condaenvbuilder.cmdline.CondaEnvironmentBuilderTool

import java.io.PrintWriter


/** Companion to [[CondaBuildWriter]].  */
object CondaBuildWriter extends BuildWriterConstants {

  /** Builds a new [[CondaBuildWriter]] for the given environment.
   *
   * @param environment the environment for which build files should be created.
   * @param output the output directory where build files should be created.
   * @param environmentYaml the path to use for the environment's conda YAML, otherwise `<output>/<env-name>.yml`.
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
            condaEnvironmentDirectory: Option[DirPath] = None): CondaBuildWriter = {
    CondaBuildWriter(
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
case class CondaBuildWriter(environment: Environment,
                            environmentYaml: PathToYaml,
                            condaBuildScript: FilePath,
                            codeBuildScript: FilePath,
                            condaEnvironmentDirectory: Option[DirPath]) extends BuildWriter {

  private lazy val condaExecutable: String = if (CondaEnvironmentBuilderTool.UseMamba) "mamba" else "conda"

  /** Writes the conda build script. */
  protected def writeCondaBuildScript(logger: Logger = this.logger): Unit = {
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

}
