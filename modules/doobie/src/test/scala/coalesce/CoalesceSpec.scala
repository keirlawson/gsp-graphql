// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package coalesce

import io.circe.literal.JsonStringContext

import utils.DatabaseSuite

final class CoalesceSpec extends DatabaseSuite {
  lazy val mapping = CoalesceMapping.fromTransactor(xa)

  test("simple coalesced query") {
    val query = """
      query {
        r {
          id
          ca {
            id
            a
          }
          cb {
            id
            b
          }
        }
      }
    """

    val expected = json"""
      {
        "data" : {
          "r" : [
            {
              "id" : "R1",
              "ca" : [
                {
                  "id" : "CA1a",
                  "a" : 10
                },
                {
                  "id" : "CA1b",
                  "a" : 11
                }
              ],
              "cb" : [
              ]
            },
            {
              "id" : "R2",
              "ca" : [
                {
                  "id" : "CA2",
                  "a" : 20
                }
              ],
              "cb" : [
                {
                  "id" : "CB2a",
                  "b" : true
                },
                {
                  "id" : "CB2b",
                  "b" : false
                }
              ]
            },
            {
              "id" : "R3",
              "ca" : [
                {
                  "id" : "CA3",
                  "a" : 30
                }
              ],
              "cb" : [
                {
                  "id" : "CB3",
                  "b" : true
                }
              ]
            }
          ]
        }
      }
    """

    val (res, trace) = mapping.compileAndRun(query).unsafeRunSync
    //println(res)

    assert(res == expected)

    val numStages = trace.size
    val numCells = trace.foldLeft(0) { case (acc, stage) =>
      acc+stage.foldLeft(0) { case (acc, stats) =>
        acc+(stats.rows*stats.cols)
      }
    }

    assert(numStages == 2)
    assert(numCells == 32)
  }
}
