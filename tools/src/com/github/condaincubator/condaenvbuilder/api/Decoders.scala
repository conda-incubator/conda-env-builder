package com.github.condaincubator.condaenvbuilder.api

import java.nio.file.{Path, Paths}

import io.circe.Decoder

import scala.util.Try

/** Collection of decoders from YAML to objects. */
object Decoders {
  implicit val DecodePath           : Decoder[Path]            = Decoder.decodeString.emapTry { str => Try(Paths.get(str)) }
  implicit val DecodeRequirement    : Decoder[Requirement]     = Requirement.decoder
  implicit val DecodeCondaStep      : Decoder[CondaStep]       = CondaStep.decoder
  implicit val DecodePipStep        : Decoder[PipStep]         = PipStep.decoder
  implicit val DecodeCodeStep       : Decoder[CodeStep]        = CodeStep.decoder
  implicit val DecodeStep           : Decoder[Step]            = Step.decoder
  implicit val DecodeEnvironment    : Decoder[Environment]     = Environment.decoder
  implicit val DecodeEnvironmentSpec: Decoder[EnvironmentSpec] = EnvironmentSpec.decoder
  implicit val DecodeSpec           : Decoder[Spec]            = Spec.decoder
}
