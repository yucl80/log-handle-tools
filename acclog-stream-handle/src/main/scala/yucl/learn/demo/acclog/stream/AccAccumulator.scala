package yucl.learn.demo.acclog.stream

import scala.collection.mutable

class AccCountAccumulator(var stack: String, var service: String,
                          var countAll: Int, var count2xx: Int, var count3xx: Int, var count4xx: Int, var count5xx: Int,var sumBytes: Long,
                          val clientSet: mutable.Set[String], val sessionSet: mutable.Set[String], val ipSet: mutable.Set[String],val urlSet:mutable.TreeSet[UriTime]){

  override def toString = s"AccCountAccumulator(stack=$stack, service=$service, countAll=$countAll, count2xx=$count2xx, count3xx=$count3xx, count4xx=$count4xx, count5xx=$count5xx, sumBytes=$sumBytes, clientCount=${clientSet.size}, sessionCount=${sessionSet.size}, ipCount=${ipSet.size},urlSet=$urlSet)"
}

class UriTime(var uri: String, var time: Double) extends Ordered[UriTime] {
  override def toString: String = {
    uri + " :" + time
  }

  def compare(that: UriTime) = {
    if (this.uri.equals(that.uri))
      0
    else
      this.time.compareTo(that.time)
  }
}
