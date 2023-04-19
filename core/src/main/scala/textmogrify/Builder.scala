/*
 * Copyright 2022 Pig.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package textmogrify

import cats.effect.kernel.{Resource, Sync}

/** Build an Analyzer or tokenizer function */
abstract class Builder(config: Config) {
  type Bldr <: Builder

  def withConfig(config: Config): Bldr

  /** Adds a lowercasing stage to the analyzer pipeline */
  def withLowerCasing: Bldr =
    withConfig(config.withLowerCasing)

  /** Adds an ASCII folding stage to the analyzer pipeline
    * ASCII folding converts alphanumeric and symbolic Unicode characters into
    * their ASCII equivalents, if one exists.
    */
  def withASCIIFolding: Bldr =
    withConfig(config.withASCIIFolding)

  def withDefaultStopWords: Bldr =
    withConfig(config.withDefaultStopWords)

  /** Adds a stop filter stage to analyzer pipeline for non-empty sets. */
  def withCustomStopWords(words: Set[String]): Bldr =
    withConfig(config.withCustomStopWords(words))

  /** Build a tokenizing function that uses the Analyzer and collects tokens in a vector */
  def tokenizer[F[_]](implicit F: Sync[F]): Resource[F, String => F[Vector[String]]]

}
