/*
 * Copyright 2010 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

import com.twitter.util.CoverageChecker
import java.util.Locale

object StorageUnit {

  def fromBytes(bytes: Long): StorageUnit = new StorageUnit(bytes)
  def fromKilobytes(kilobytes: Long): StorageUnit = new StorageUnit(kilobytes * 1024)
  def fromMegabytes(megabytes: Long): StorageUnit = new StorageUnit(megabytes * 1024 * 1024)
  def fromGigabytes(gigabytes: Long): StorageUnit = new StorageUnit(gigabytes * 1024 * 1024 * 1024)
  def fromTerabytes(terabytes: Long): StorageUnit =
    new StorageUnit(terabytes * 1024 * 1024 * 1024 * 1024)
  def fromPetabytes(petabytes: Long): StorageUnit =
    new StorageUnit(petabytes * 1024 * 1024 * 1024 * 1024 * 1024)
  def fromExabytes(exabytes: Long): StorageUnit =
    new StorageUnit(exabytes * 1024 * 1024 * 1024 * 1024 * 1024 * 1024)

  val infinite: StorageUnit = new StorageUnit(Long.MaxValue)
  val zero: StorageUnit = new StorageUnit(0)

  private def factor(s: String): Long = {
    CoverageChecker.initialize("factor", 10)
    var lower = s.toLowerCase
    if (lower endsWith "s") {
      CoverageChecker.reached("factor", 0)
      lower = lower dropRight 1
    } else {
      CoverageChecker.reached("factor", 1)
    }
      
    lower match {
      case "byte" => 
        CoverageChecker.reached("factor", 2)
        1L
      case "kilobyte" =>
        CoverageChecker.reached("factor", 3) 
        1L << 10
      case "megabyte" => 
        CoverageChecker.reached("factor", 4)
        1L << 20
      case "gigabyte" => 
        CoverageChecker.reached("factor", 5)
        1L << 30
      case "terabyte" => 
        CoverageChecker.reached("factor", 6)
        1L << 40
      case "petabyte" => 
        CoverageChecker.reached("factor", 7)
        1L << 50
      case "exabyte" => 
        CoverageChecker.reached("factor", 8)
        1L << 60
      case badUnit => 
        CoverageChecker.reached("factor", 9)
        throw new NumberFormatException("Unrecognized unit %s".format(badUnit))
    }
  }

  /**
   * Note, this can cause overflows of the Long used to represent the
   * number of bytes.
   */
  def parse(s: String): StorageUnit = s.split("\\.") match {
    case Array(v, u) =>
      val vv = v.toLong
      val uu = factor(u)
      new StorageUnit(vv * uu)

    case _ =>
      throw new NumberFormatException("invalid storage unit string: %s".format(s))
  }
}

/**
 * Representation of storage units.
 *
 * Use either `StorageUnit.fromX` or [[com.twitter.conversions.storage implicit conversions]]
 * from `Long` and `Int` to construct instances.
 *
 * {{{
 *   import com.twitter.conversions.StorageUnitOps._
 *   import com.twitter.util.StorageUnit
 *
 *   val size: StorageUnit = 10.kilobytes
 * }}}
 *
 * @note While all the methods in this abstraction are prefixed according
 *       to the International System of Units (kilo-, mega-, etc), they
 *       actually operate on the 1024 scale (not 1000).
 *
 * @note Operations can cause overflows of the `Long` used to represent the
 *       number of bytes.
 */
class StorageUnit(val bytes: Long) extends Ordered[StorageUnit] {
  def inBytes: Long = bytes
  def inKilobytes: Long = bytes / 1024L
  def inMegabytes: Long = bytes / (1024L * 1024)
  def inGigabytes: Long = bytes / (1024L * 1024 * 1024)
  def inTerabytes: Long = bytes / (1024L * 1024 * 1024 * 1024)
  def inPetabytes: Long = bytes / (1024L * 1024 * 1024 * 1024 * 1024)
  def inExabytes: Long = bytes / (1024L * 1024 * 1024 * 1024 * 1024 * 1024)

  def +(that: StorageUnit): StorageUnit = new StorageUnit(this.bytes + that.bytes)
  def -(that: StorageUnit): StorageUnit = new StorageUnit(this.bytes - that.bytes)
  def *(scalar: Double): StorageUnit = new StorageUnit((this.bytes.toDouble * scalar).toLong)
  def *(scalar: Long): StorageUnit = new StorageUnit(this.bytes * scalar)
  def /(scalar: Long): StorageUnit = new StorageUnit(this.bytes / scalar)

  // Java-friendly API for binary operations.
  def plus(that: StorageUnit): StorageUnit = this + that
  def minus(that: StorageUnit): StorageUnit = this - that
  def times(scalar: Double): StorageUnit = this * scalar
  def times(scalar: Long): StorageUnit = this * scalar
  def divide(scalar: Long): StorageUnit = this / scalar

  override def equals(other: Any): Boolean = other match {
    case other: StorageUnit =>
      inBytes == other.inBytes
    case _ =>
      false
  }

  override def hashCode: Int = bytes.hashCode

  override def compare(other: StorageUnit): Int =
    if (bytes < other.bytes) -1 else if (bytes > other.bytes) 1 else 0

  def min(other: StorageUnit): StorageUnit =
    if (this < other) this else other

  def max(other: StorageUnit): StorageUnit =
    if (this > other) this else other

  override def toString(): String = s"$inBytes.bytes"

  def toHuman(): String = {
    val prefix = "KMGTPE"
    var prefixIndex = -1
    var display = bytes.toDouble.abs
    while (display > 1126.0) {
      prefixIndex += 1
      display /= 1024.0
    }
    if (prefixIndex < 0) {
      "%d B".format(bytes)
    } else {
      "%.1f %ciB".formatLocal(Locale.ENGLISH, display * bytes.signum, prefix.charAt(prefixIndex))
    }
  }
}
