package com.github.condaincubator.condaenvbuilder.api

import io.circe._
import io.circe.syntax._

/** A single step in building an environment. */
trait Step {
  /** Returns a new step where this step inherits from one or more other steps. */
  def inheritFrom(step: Step*): Step = this

  /** Returns true if this step can inherit from the other step */
  def canInheritFrom(step: Step): Boolean = this.getClass == step.getClass && step.ne(this)
}

object Step {
  import Encoders.{EncodeCodeStep, EncodeCondaStep, EncodePipStep}

  /** Returns a YAML encoder for [[Step]] */
  def encoder: Encoder[Step] = new Encoder[Step] {
    final def apply(step: Step): Json = step match {
      case conda: CondaStep => Json.obj(("conda", conda.asJson))
      case pip: PipStep     => Json.obj(("pip", pip.asJson))
      case code: CodeStep   => Json.obj(("code", code.asJson))
      case _                => throw new IllegalArgumentException(s"Could not encode the step: $step")
    }
  }

  /** Returns a YAML decoder for [[Step]] */
  def decoder: Decoder[Step] = new Decoder[Step] {
    import Decoders.{DecodeCodeStep, DecodeCondaStep, DecodePipStep}

    final def apply(c: HCursor): Decoder.Result[Step] = {
      // Get the keys at this level
      val keys: Seq[String] = c.keys.map(_.toSeq).getOrElse(Seq.empty)
      // Parse the keys
      keys.collectFirst {
        case "conda" => c.downField("conda").as[CondaStep]
        case "pip"   => c.downField("pip").as[PipStep]
        case "code"  => c.downField("code").as[CodeStep]
      }.getOrElse {
        Left(DecodingFailure("Step should be one of 'conda', 'pip', or 'code'", c.history))
      }
    }
  }
}

/** A step where the default environment can be applied. */
trait StepWithDefaults extends Step {
  /** Returns a new step with the defaults in the given step applied to this step. */
  def withDefaults(defaults: Step): StepWithDefaults
}
