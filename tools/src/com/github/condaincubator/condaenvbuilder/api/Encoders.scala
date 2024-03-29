package com.github.condaincubator.condaenvbuilder.api

import java.nio.file.Path

import io.circe.{Encoder, Json}

/** Collection of encoders from YAML to objects. */
object Encoders {
  implicit val EncodePath       : Encoder[Path]        = (path: Path) => Json.fromString(path.toString)
  implicit val EncodeRequirement: Encoder[Requirement] = Requirement.encoder
  implicit val EncodeCondaStep  : Encoder[CondaStep]   = CondaStep.encoder
  implicit val EncodePipStep    : Encoder[PipStep]     = PipStep.encoder
  implicit val EncodeCodeStep   : Encoder[CodeStep]    = CodeStep.encoder
  implicit val EncodeStep       : Encoder[Step]        = Step.encoder
  implicit val EncodeEnvironment: Encoder[Environment] = Environment.encoder
  implicit val EncodeSpec       : Encoder[Spec]        = Spec.encoder
}
