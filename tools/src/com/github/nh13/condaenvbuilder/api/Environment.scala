package com.github.nh13.condaenvbuilder.api

import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}


/** A single conda environment.  This environment is built with multiple steps, which may include adding conda packages,
  * pip packages, and commands to install packages from source (or otherwise).  Multiple environments may belong to the
  * same "group", for example if they should be built together in the same docker image.
  *
  * @param name the name of this environment
  * @param steps the steps to build this environment
  * @param group the name of the group to which this environment belongs.
  */
case class Environment(name: String, steps: Seq[Step] = Seq.empty, group: String) {

  /** Inherit steps from the given environment.
    *
    * If the current environment contains a step of the same type as the inherited environment, then the former step
    * will inherit from the latter step.  If no step in the current environment of the same type as the inherited
    * environment exists, the the step to be inherited will be added.  This process is applied iteratively, so that only
    * the new environment will have only one step of each type.
    *
    * @param environment the environment to inherit from
    * @return a new environment with inherited steps
    */
  def inheritFrom(environment: Environment*): Environment = {
    if (environment.isEmpty) this
    else this.coalesce((this.steps ++ environment.flatMap(_.steps)):_*)
  }

  /** Applies the default environment to this environment.
    *
    * Each step in the default environment will be applied to each step in this environment.  This allows default
    * package versions, conda channels, and other step-specific defaults to be applied.
    *
    * @param defaults the default environment.
    * @return a new environment with the default environment applied
    */
  def withDefaults(defaults: Environment): Environment = withDefaults(defaults.steps:_*)

  /** Applies the default step(s) to this environment.
    *
    * Each default step will be applied to each step in this environment.  This allows default package versions, conda
    * channels, and other step-specific defaults to be applied.
    *
    * @param defaults the default step(s) to apply.
    * @return a new environment with the default steps applied
   */
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

  /** Coalesce the steps in the current environment such that there are only one step of each type.  Earlier steps will
    * take priority over later steps.
    *
    * Go through all steps, starting with an empty list of steps, and trying to inherit from a step one-by-one. If no
    * steps in the current list of steps can inherit from the given parent, then just add the parent.  This handles
    * the case where this environment does not contain a step of the inherited type, for example, this environment
    * lacks Pip steps, but inherits one.
    *
    * @param step additional steps to incorporate
    * @return
    */
  def coalesce(step: Step*): Environment = {
    val updated: Seq[Step] = (this.steps ++ step).foldLeft(Seq.empty[Step]) { case (steps: Seq[Step], parentStep: Step) =>
      steps.find { curStep: Step => curStep.canInheritFrom(parentStep) } match {
        case None                => steps :+ parentStep
        case Some(curStep: Step) => steps.filterNot(_ == curStep) :+ curStep.inheritFrom(parentStep)
      }
    }
    this.copy(steps=updated.toIndexedSeq)
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

  /** Returns an YAML encoder for [[Environment]] */
  def encoder: Encoder[Environment] = new Encoder[Environment] {
    final def apply(environment: Environment): Json = Json.obj(
      ("group", environment.group.asJson),
      ("steps", Json.fromValues(environment.steps.map(_.asJson)))
    )
  }

  /** Returns a YAML decoder for [[Environment]] */
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