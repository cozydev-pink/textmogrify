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

final case class Config(
    lowerCase: Boolean,
    foldASCII: Boolean,
    defaultStopWords: Boolean,
    customStopWords: Set[String],
) {
  def withLowerCasing: Config =
    copy(lowerCase = true)

  def withASCIIFolding: Config =
    copy(foldASCII = true)

  def withDefaultStopWords: Config =
    copy(defaultStopWords = true)

  def withCustomStopWords(words: Set[String]): Config =
    copy(customStopWords = words)
}
object Config {
  def empty: Config = Config(false, false, false, Set.empty)
}
