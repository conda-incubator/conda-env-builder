package com.github.nh13.condaenvbuilder.api

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}

/** Wrapper class for [[Environment]] that stores from which environment(s) this environment inher.ts
  *
  * @param environment the [[Environment]]
  * @param inherits the names of the [[Environment]]s from which this environment inherits
  */
case class EnvironmentSpec(environment: Environment, inherits: Seq[String] = Seq.empty) {
  /** Returns a new environment specification after applying inheritance from the given environment. */
  def inheritFrom(environment: Environment): EnvironmentSpec = {
    if (!this.inherits.contains(environment.name)) this else {
      this.copy(
        environment = this.environment.inheritFrom(environment),
        inherits    = this.inherits.filterNot(_ == environment.name)
      )
    }
  }
}

object EnvironmentSpec {
  // Developer note: we do not have an encoder as we assume that the environment is compiled before it is encoded.

  /** Returns a YAML decoder for [[EnvironmentSpec]] */
  def decoder: Decoder[EnvironmentSpec] = new Decoder[EnvironmentSpec] {
    import Decoders.DecodeEnvironment

    final def apply(c: HCursor): Decoder.Result[EnvironmentSpec] = {
      val keys: Seq[String] = c.keys.map(_.toSeq).getOrElse(Seq.empty)

      val environmentResult: Result[Environment] = DecodeEnvironment.apply(c)

      val inheritsResult: Result[Seq[String]] = {
        if (keys.contains("inherits")) c.downField("inherits").as[Seq[String]]
        else Right(Seq.empty)
      }

      for {
        environment <- environmentResult
        inherits <- inheritsResult
      } yield {
        EnvironmentSpec(environment=environment, inherits=inherits)
      }
    }
  }
}