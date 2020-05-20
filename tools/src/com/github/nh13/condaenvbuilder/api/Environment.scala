package com.github.nh13.condaenvbuilder.api

import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}

case class Environment(name: String, steps: Seq[Step], group: String) {
  def withDefaults(defaults: Environment): Environment = withDefaults(defaults.steps:_*)

  def withDefaults(defaults: Step*): Environment = {
    val steps = this.steps.map {
      case step: StepWithDefaults =>
        defaults.foldLeft(step) {
          case (curStep: StepWithDefaults, defaultStep: Step) => curStep.withDefaults(defaults=defaultStep)
          case (curStep: StepWithDefaults, _)                 => curStep
        }
      case step: Step             => step
    }
    this.copy(steps=steps.distinct)
  }

  def inheritFrom(environment: Environment*): Environment = {
    if (environment.isEmpty) this
    else {
      val (initial, rest) = {
        if (this.steps.isEmpty) (environment.head.steps, environment.drop(1))
        else (this.steps, environment)
      }
      val updated = initial.map { step =>
        rest.flatMap(_.steps).foldLeft(step) { case (curStep: Step, parentStep: Step) =>
          curStep.inheritFrom(step = parentStep)
        }
      }
      this.copy(steps=updated)
    }
  }
}

/** Example YAML encoding for [[Environment]]
  * {{{
  *   environment_name:
  *     group: group_name
  *     inherits:
  *     - other_environment_name_a
  *     - other_environment_name_b
  *     steps:
  *     - conda:
  *       ...
  *     - pip:
  *       ...
  *     - code:
  *       ...
  * }}}
  *
  * All keys are optional.
  */
object Environment {
  val NotYetDecodedName: String = "not-yet-compiled"
  val NotYetDecodedGroup: String = "not-yet-compiled"

  import Encoders.EncodeStep

  def encoder: Encoder[Environment] = new Encoder[Environment] {
    final def apply(environment: Environment): Json = Json.obj(
      ("group", environment.group.asJson),
      ("steps", Json.fromValues(environment.steps.map(_.asJson)))
    )
  }

  def decoder: Decoder[Environment] = new Decoder[Environment] {
    import Decoders.DecodeStep
    final def apply(c: HCursor): Decoder.Result[Environment] = {
      val keys: Seq[String] = c.keys.map(_.toSeq).getOrElse(Seq.empty)
      val group = {
        if (keys.contains("group")) c.downField("group").as[String]
        else Right(NotYetDecodedGroup)
      }

      group.flatMap { _group =>
        if (c.keys.exists(_.toSeq.contains("steps"))) {
          for {
            steps <- c.downField("steps").as[Seq[Step]]
          } yield {
            Environment(name=NotYetDecodedGroup, steps=steps, group=_group)
          }
        }
        else {
          Right(Environment(name=NotYetDecodedGroup, steps=Seq.empty, group=_group))
        }
      }
    }
  }
}