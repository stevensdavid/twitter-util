package com.twitter.util

import scala.collection.mutable.HashMap

object CoverageChecker {
    val map: HashMap[String, Array[Boolean]] = new HashMap()
    def initialize(functionName: String, branchCount: Integer): Unit = {
        if (!map.contains(functionName)) {
            map += (functionName -> new Array(branchCount))
        }
    }

    def reached(functionName: String, branch: Integer): Unit = {
        var reachedBranches = map.getOrElse(functionName, sys.error(s"unexpected key: $functionName"))
        reachedBranches(branch) = true
        map += (functionName -> reachedBranches)
    }
}