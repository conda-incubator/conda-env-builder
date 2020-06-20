package com.github.nh13.condaenvbuilder.api

import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}

/** Specifies the pip arguments and requirements to be installed a conda environment.  It is recommended that
  * packages should only be installed by pip if the package is not available in a conda channel.
  *
  * @param args the arguments to pip in the conda YAML file.
  * @param requirements the pip package requirements.
  */
case class PipStep(args: Seq[String]=Seq.empty, requirements: Seq[Requirement]=Seq.empty) extends StepWithDefaults {
  /** Inherit (in-order) args and requirements from the given step(s).
    *
    * @param step one or more steps from which to inherit.
    */
  override def inheritFrom(step: Step*): PipStep = {
    val steps = step.collect { case s: PipStep => s }
    // Developer note: we could try to remove duplicate arguments, for example with Sopt, but that is the beyond the
    // scope here.
    this.copy(
      args         = (args ++ steps.flatMap(_.args)).distinct,
      requirements = Requirement.join(parent=requirements, child=steps.flatMap(_.requirements))
    )
  }

  /** Applies the default step to this step.  The default args are prepended to the current list of args.  Any
    * requirement that has a default version is updated (and must be present in the default step).
    *
    * @param defaults the default step.
    */
  def withDefaults(defaults: Step): StepWithDefaults = defaults match {
    case _defaults: PipStep =>
      this.copy(
        args         = (_defaults.args ++ this.args).distinct,
        requirements = Requirement.withDefaults(requirements = this.requirements, defaults = _defaults.requirements),
      )
    case _ => this
  }
}

/** Example YAML encoding for [[PipStep]]
  * {{{
  *   - pip:
  *     args:
  *       - --upgrade-strategy
  *       - only-if-needed
  *     requirements:
  *       - defopt==5.1.0
  *       - samwell
  * }}}
  *
  * Both `args` and `requirements` are optional.
  *
  * Requirements may omit version numbers, which can be compiled from the default environment
  */
object PipStep {

  import Encoders.EncodeRequirement

  /** Returns an YAML encoder for [[PipStep]] */
  def encoder: Encoder[PipStep] = new Encoder[PipStep] {
    final def apply(step: PipStep): Json = Json.obj(
      ("args", Json.fromValues(step.args.map(_.asJson))),
      ("requirements", Json.fromValues(step.requirements.map(_.asJson)))
    )
  }

  /** Returns a YAML decoder for [[PipStep]] */
  def decoder: Decoder[PipStep] = new Decoder[PipStep] {
    import Decoders.DecodeRequirement
    final def apply(c: HCursor): Decoder.Result[PipStep] = {
      val keys: Seq[String] = c.keys.map(_.toSeq).getOrElse(Seq.empty)

      val argsResults: Result[Seq[String]] = {
        if (keys.contains("args")) c.downField("args").as[Seq[String]]
        else Right(Seq.empty)
      }

      val requirementsResults: Result[Seq[Requirement]] = {
        if (keys.contains("requirements")) c.downField("requirements").as[Seq[Requirement]]
        else Right(Seq.empty)
      }

      for {
        args <- argsResults
        requirements <- requirementsResults
      } yield {
        PipStep(args=args, requirements=requirements)
      }
    }
  }
}
