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

import cats.effect._
import fs2.{Chunk, Stream, Pull}
import fs2.io.toInputStreamResource
import fs2.text
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import scala.collection.mutable.ArrayBuffer
import java.io.Reader
import java.io.InputStreamReader
import org.apache.lucene.analysis.Analyzer

private[lucene] sealed abstract class TokenGetter {
  def increment(): Boolean
  def string: String
  def close(): Unit
}

/** AnalyzerPipe provides methods to tokenize a possibly very long `Stream[F, String]`
  * or `Stream[F, Byte]`, such as from a file. When possible, prefer starting with a
  * `Stream[F, Byte]` and use `tokenizeBytes`.
  */
sealed abstract case class AnalyzerPipe[F[_]](readerF: Reader => Resource[F, TokenGetter])(implicit
    F: Async[F]
) {

  private def streamToTokenGetter(input: Stream[F, Byte]): Resource[F, TokenGetter] =
    toInputStreamResource(input).flatMap { in =>
      readerF(new InputStreamReader(in))
    }

  /** Emits a string for every token, as determined by the Analyzer, in the input stream.
    * Decoding from bytes to strings is done using the default charset.
    *
    * @param in
    *   input stream to tokenize
    * @param tokenN
    *   maximum number of tokens to read at a time
    */
  def tokenizeBytes(
      in: Stream[F, Byte],
      tokenN: Int,
  ): Stream[F, String] = {
    def loop: TokenGetter => Pull[F, String, Option[TokenGetter]] = tk => {
      val arr = new ArrayBuffer[String](tokenN)
      var iter = 0
      while (iter < tokenN && tk.increment()) {
        arr.append(tk.string)
        iter += 1
      }
      if (iter < tokenN)
        // we finished early, no tokens left, final chunk
        Pull.output(Chunk.from(arr)).as(None)
      else
        Pull.output(Chunk.from(arr)).as(Some(tk))
    }

    def go(tokens: Stream[F, TokenGetter]): Pull[F, String, Unit] =
      tokens.pull.uncons1.flatMap {
        case Some((tk, _)) => Pull.loop(loop)(tk)
        case None => Pull.done
      }
    go(Stream.resource(streamToTokenGetter(in))).stream
  }

  /** Emits a string for every token, as determined by the Analyzer, in the input stream. A space is
    * inserted between each element in the input stream to avoid accidentally combining words. See
    * `tokenizeStringsRaw` to avoid this behaviour.
    *
    * @param in
    *   input stream to tokenize
    * @param tokenN
    *   maximum number of tokens to read at a time
    */
  def tokenizeStrings(
      in: Stream[F, String],
      tokenN: Int,
  ): Stream[F, String] =
    tokenizeBytes(in.intersperse(" ").through(text.utf8.encode), tokenN)

  /** Emits a string for every token, as determined by the Analyzer, in the input stream. Becareful,
    * the end of one string will be joined with the beginning of the next in the Analyzer. See
    * `tokenizeStrings` to automatically intersperse spaces.
    *
    * @param in
    *   input stream to tokenize
    * @param tokenN
    *   maximum number of tokens to read at a time
    */
  def tokenizeStringsRaw(
      in: Stream[F, String],
      tokenN: Int,
  ): Stream[F, String] =
    tokenizeBytes(in.through(text.utf8.encode), tokenN)

}
object AnalyzerPipe {

  /** Build an AnalyzerPipe from a Resource wrapped Analyzer
    */
  def fromResource[F[_]](analyzerR: Resource[F, Analyzer])(implicit F: Async[F]): AnalyzerPipe[F] =
    new AnalyzerPipe[F](reader =>
      analyzerR
        .evalMap { analyzer =>
          val ts = analyzer.tokenStream("textmogrify-field", reader)
          val termAtt = ts.addAttribute(classOf[CharTermAttribute])
          F.delay {
            ts.reset()
            new TokenGetter {
              def increment() = ts.incrementToken()
              def string = termAtt.toString()
              def close() = ts.close()
            }
          }
        }
    ) {} // Needed to instantiate abstract class

}
