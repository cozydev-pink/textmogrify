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

package textmogrify.lucene

import cats.effect.Resource
import cats.effect.kernel.Sync
import scala.collection.mutable.ArrayBuffer
import java.io.StringReader
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.Analyzer

object Tokenizer {

  /** Build a tokenizing function that runs its input through the Analyzer and collects
    * all tokens into a `Vector`
    */
  def vectorTokenizer[F[_]](
      analyzer: Resource[F, Analyzer]
  )(implicit F: Sync[F]): Resource[F, String => F[Vector[String]]] =
    analyzer.map { analyzer => (s: String) =>
      F.delay {
        val ts = analyzer.tokenStream("textmogrify-field", new StringReader(s))
        val termAtt = ts.addAttribute(classOf[CharTermAttribute])
        val arr = new ArrayBuffer[String]
        try {
          ts.reset()
          while (ts.incrementToken())
            arr.append(termAtt.toString())
          ts.end()
        } finally
          ts.close()
        arr.toVector
      }
    }
}
