package com.github.nh13.condaenvbuilder.api

import java.nio.file.Path

import io.circe.{Encoder, Json}

/** Collection of encoders from YAML to objects. */

object Encoders {

  implicit val EncodePath       : Encoder[Path]            = new Encoder[Path] {
    final def apply(path: Path): Json = Json.fromString(path.toString)
  }
  implicit val EncodeRequirement: Encoder[Requirement]            = Requirement.encoder
  implicit val EncodeCondaStep  : Encoder[CondaStep] = CondaStep.encoder
  implicit val EncodePipStep    : Encoder[PipStep] = PipStep.encoder
  implicit val EncodeCodeStep   : Encoder[CodeStep] = CodeStep.encoder
  implicit val EncodeStep       : Encoder[Step] = Step.encoder
  implicit val EncodeEnvironment: Encoder[Environment] = Environment.encoder
  implicit val EncodeSpec       : Encoder[Spec] = Spec.encoder
}
