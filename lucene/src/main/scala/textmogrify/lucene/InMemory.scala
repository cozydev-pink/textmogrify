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

import cats.syntax.all._
import cats.effect.{IO, IOApp, Resource}
import fs2.{Stream, Pull, Chunk}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.memory.MemoryIndex
import org.apache.lucene.queryparser.classic.QueryParser

object InMemory extends IOApp.Simple {

  case class Doc(author: String, title: String)

  val docStream = Stream[IO, Doc](
    Doc("Tales of James", "Readings about Salmons and other select Alaska fishing Manuals"),
    Doc("Tales of Andrew", "Readings about Salmons and other select Alaska fishing Manuals"),
    Doc("james stuff", "salmon are great fish, learn cook em' in this manual"),
  )

  val analyzerR = AnalyzerBuilder.english.withLowerCasing.build[IO]
  val indexR = Resource.eval(IO(new MemoryIndex()))

  def searchPipe(analyzer: Analyzer, index: MemoryIndex)(
      input: Stream[IO, Doc]
  ): Pull[IO, Float, Unit] = {
    val parser = new QueryParser("content", analyzer)
    def goChunk(c: Chunk[Doc]): Chunk[Float] =
      c.map { d =>
        index.addField("content", d.title, analyzer)
        index.addField("author", d.author, analyzer)
        val score = index.search(parser.parse("+author:james +salmon~ +fish* manual~"))
        index.reset()
        score
      }
    def goStream(s: Stream[IO, Doc]): Pull[IO, Float, Unit] =
      s.pull.uncons.flatMap {
        case Some((hd, tl)) => Pull.output(goChunk(hd)) >> goStream(tl)
        case None => index.reset(); Pull.done
      }
    goStream(input)
  }

  val indexer = (analyzerR, indexR).parTupled.map { case (a, i) =>
    searchPipe(a, i)(docStream).stream
  }

  val run = Stream.resource(indexer).flatten.compile.toList.flatMap(IO.println)
}
