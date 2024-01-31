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
package lucene

import munit.CatsEffectSuite
import cats.effect._

class WhitespaceBuilderSuite extends CatsEffectSuite {

  val jalapenos = "I Like Jalapeños"

  test("whitespace analyzer default should tokenize without any transformations") {
    val analyzer = Builder.whitespace
    val actual = analyzer.tokenizer[IO].use(f => f(jalapenos))
    assertIO(actual, Vector("I", "Like", "Jalapeños"))
  }

  test("whitespace analyzer withLowerCasing should lowercase all letters") {
    val analyzer = Builder.whitespace.withLowerCasing
    val actual = analyzer.tokenizer[IO].use(f => f(jalapenos))
    assertIO(actual, Vector("i", "like", "jalapeños"))
  }

  test("whitespace analyzer withASCIIFolding should fold 'ñ' to 'n'".fail) {
    val analyzer = Builder.whitespace.withASCIIFolding
    val actual = analyzer.tokenizer[IO].use(f => f(jalapenos))
    assertIO(actual, Vector("I", "Like", "Jalapenos"))
  }

  test("whitespace analyzer withCustomStopWords should filter them out") {
    val analyzer = Builder.whitespace.withCustomStopWords(Set("I"))
    val actual = analyzer.tokenizer[IO].use(f => f(jalapenos))
    assertIO(actual, Vector("Like", "Jalapeños"))
  }

}
