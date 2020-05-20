package com.github.nh13.condaenvbuilder.io

import java.io.Writer

import com.fulcrumgenomics.commons.io.Io
import com.github.nh13.condaenvbuilder.CondaEnvironmentBuilderDef.PathToYaml
import com.github.nh13.condaenvbuilder.api.{Encoders, Spec}
import io.circe.syntax._
import io.circe.yaml.Printer

/** Write methods for a conda environment specification (see [[Spec]]) */
object SpecWriter {

  import Encoders.EncodeSpec

  /** The default printer for YAML files. */
  private val DefaultYamlPrinter: Printer = Printer.spaces2

  /** Writes the given conda environment specification.
    *
    * @param spec the conda environment specifications
    * @param config the output YAML path
    * @param printer the YAML printer to use
    */
  def write(spec: Spec, config: PathToYaml, printer: Printer): Unit = {
    val writer = Io.toWriter(path=config)
    this.write(spec=spec, writer=writer, printer=printer)
    writer.close()
  }

  /** Writes the given conda environment specification.
    *
    * @param spec the conda environment specifications
    * @param writer the writer to which the YAML is written
    * @param printer the YAML printer to use
    */
  def write(spec: Spec, writer: Writer, printer: Printer): Unit = {
    writer.write(printer.pretty(spec.asJson))
    writer.flush()
  }

  /** Writes the given conda environment specification.
    *
    * @param spec the conda environment specifications
    * @param config the output YAML path
    */
  def write(spec: Spec, config: PathToYaml): Unit = write(spec=spec, config=config, printer=DefaultYamlPrinter)

  /** Writes the given conda environment specification.
    *
    * @param spec the conda environment specifications
    * @param writer the writer to which the YAML is written
    */
  def write(spec: Spec, writer: Writer): Unit = write(spec=spec, writer=writer, printer=DefaultYamlPrinter)
}