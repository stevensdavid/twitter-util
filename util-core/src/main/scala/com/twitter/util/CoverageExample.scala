package com.twitter.util

import com.twitter.util._

object CoverageExample {
  def main (args: Array[String] ): Unit = {
    CoverageChecker.initialize("f1", 5)
    CoverageChecker.initialize("f2", 2)
    CoverageChecker.reached("f1", 3)
    println(CoverageChecker.map.getOrElse("f1", sys.error(s"unexpected key")).mkString("[",", ", "]"))
    println(CoverageChecker.map.getOrElse("f2", sys.error(s"unexpected key")).mkString("[",", ", "]"))
  }
}
