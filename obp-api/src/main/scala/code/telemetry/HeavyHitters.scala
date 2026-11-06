/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.telemetry

import scala.collection.mutable

/**
 * This class keeps the heavy hitters of a stream (its most frequent keys) in a table of fixed size, using the Space-Saving
 * algorithm (Metwally, Agrawal and El Abbadi, "Efficient Computation of Frequent and Top-k Elements
 * in Data Streams", 2005).
 *
 * The problem it solves: counting every key of a stream (every caller, every caller and endpoint)
 * takes memory without limit, and a caller can inflate it on purpose by varying its key. This table
 * never grows past `capacity`. With N offers counted, every key offered more than N / capacity times
 * is guaranteed to be in it, and each entry's `count` is at most its `error` above the true count.
 *
 * How an offer is counted:
 *  - a key already in the table: its count goes up by one;
 *  - a new key and a free slot: it is added with count 1 and error 0;
 *  - a new key and a full table: the entry with the smallest count (call it min) is replaced by the
 *    new key, with count min + 1 and error min. The new entry's details start from this offer.
 *
 * The paper finds the smallest entry with a linked structure of count buckets. At the sizes used
 * here (hundreds of slots), a scan only when a full table meets a new key costs microseconds, and is
 * simpler to get right.
 *
 * `D` is a mutable details record kept per entry (status counts, durations and so on). Details are
 * exact from the moment the key entered the table; only `count` covers the key's whole history
 * within its bound.
 *
 * All methods are synchronised: one instance is shared by every request thread of its minute.
 *
 * References (credit to the authors of the ideas used here):
 *  - Space-Saving, the algorithm this class implements: Ahmed Metwally, Divyakant Agrawal and
 *    Amr El Abbadi, "Efficient Computation of Frequent and Top-k Elements in Data Streams",
 *    Proceedings of the 10th International Conference on Database Theory (ICDT 2005), Lecture Notes
 *    in Computer Science 3363, Springer, 2005, pages 398-412.
 *  - The earlier algorithm it refines, the first for finding frequent items in bounded space:
 *    Jayadev Misra and David Gries, "Finding Repeated Elements", Science of Computer Programming 2(2),
 *    1982, pages 143-152.
 *  - Combining per-minute tables into a longer window (see TrafficSources) relies on these summaries
 *    being mergeable: Pankaj K. Agarwal, Graham Cormode, Zengfeng Huang, Jeff M. Phillips, Zhewei Wei
 *    and Ke Yi, "Mergeable Summaries", Proceedings of the 31st ACM Symposium on Principles of
 *    Database Systems (PODS 2012), pages 23-34.
 */
final class HeavyHitters[K, D](val capacity: Int, newDetails: () => D) {

  require(capacity > 0, "HeavyHitters capacity must be positive")

  final class Entry private[HeavyHitters] (val key: K, var count: Long, var error: Long, val details: D)

  private val entries = mutable.HashMap.empty[K, Entry]
  private var offered: Long = 0L

  /** Counts one offer of `key` and applies `update` to its details. */
  def offer(key: K)(update: D => Unit): Unit = synchronized {
    offered += 1
    val entry = entries.get(key) match {
      case Some(existing) =>
        existing.count += 1
        existing
      case None if entries.size < capacity =>
        val added = new Entry(key, 1L, 0L, newDetails())
        entries.put(key, added)
        added
      case None =>
        val smallest = entries.valuesIterator.minBy(_.count)
        entries.remove(smallest.key)
        val replacement = new Entry(key, smallest.count + 1, smallest.count, newDetails())
        entries.put(key, replacement)
        replacement
    }
    update(entry.details)
  }

  /** True when every slot is taken, so a key missing from the table may still have up to [[minCount]] offers. */
  def isFull: Boolean = synchronized(entries.size >= capacity)

  /** The smallest count in the table (0 when empty). */
  def minCount: Long = synchronized(if (entries.isEmpty) 0L else entries.valuesIterator.map(_.count).min)

  /** How many offers the table has counted. */
  def totalOffered: Long = synchronized(offered)

  /** The entries, as a copy taken under the lock (the details are shared; read them, do not change them). */
  def snapshot: List[Entry] = synchronized(entries.values.toList)
}
