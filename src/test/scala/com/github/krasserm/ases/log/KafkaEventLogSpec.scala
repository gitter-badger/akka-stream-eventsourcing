/*
 * Copyright 2017 the original author or authors.
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

package com.github.krasserm.ases.log

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import com.github.krasserm.ases.DeliveryProtocol.{Delivered, Recovered}
import com.github.krasserm.ases.StreamSpec
import org.apache.kafka.common.TopicPartition
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpecLike}

import scala.collection.immutable.Seq

class KafkaEventLogSpec extends TestKit(ActorSystem("test")) with WordSpecLike with Matchers with ScalaFutures with StreamSpec with KafkaSpec {
  implicit val pc = PatienceConfig(timeout = Span(5, Seconds), interval = Span(10, Millis))

  val kafkaEventLog: KafkaEventLog = new KafkaEventLog(host, port)

  "A Kafka event log" must {
    "provide a sink for writing events and a source for delivering replayed events" in {
      val topicPartition = new TopicPartition("p-1", 0)
      val events = Seq("a", "b", "c")

      Source(events).runWith(kafkaEventLog.sink(topicPartition)).futureValue
      kafkaEventLog.source[String](topicPartition).runWith(Sink.seq).futureValue should be(events)
    }
    "provide a flow with an input port for writing events and and output port for delivering replayed and live events" in {
      val topicPartition = new TopicPartition("p-2", 0)
      val events1 = Seq("a", "b", "c")
      val events2 = Seq("d", "e", "f")

      val expected = (events1.map(Delivered(_)) :+ Recovered) ++ events2.map(Delivered(_))

      Source(events1).runWith(kafkaEventLog.sink(topicPartition)).futureValue
      Source(events2).via(kafkaEventLog.flow(topicPartition)).take(7).runWith(Sink.seq).futureValue should be(expected)
    }
  }
}
