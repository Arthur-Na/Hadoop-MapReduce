import java.io.{BufferedWriter, File, FileWriter}
import scala.sys.process._
import scala.concurrent._
import ExecutionContext.Implicits.global

import scala.io.Source

object Slave extends App {
  def countWord_sToUm(filename: String): Unit = {
    val filenumber = ("""\d+""".r findAllIn filename).toList(0)
    val file = new File("/tmp/naegel_a/maps/UM" + filenumber + ".txt")
    val bw = new BufferedWriter(new FileWriter(file))

    val lines = Source
      .fromFile(filename)
      .getLines
      .toList
    val count =
      lines.flatMap(l => l.split(" ")).groupBy(identity).mapValues(_.size)
    //val word = lines.flatMap(l => l.split(" "))
    count.foreach { tupl =>
      bw.write(tupl._1 + " " + tupl._2 + "\n")
      println(tupl._1)
    }
    bw.close()

    //word.distinct.foreach(println)
  }

  //def umToSm() = {}
  def cmd(commande: Seq[String]) = {
    val slrun = commande.run //lineStream_! ProcessLogger(line => ())
    val f = Future(blocking(slrun.exitValue())) // wrap in Future
    try {
      Await.result(f, duration.Duration(10, "sec"))
    } catch {
      case _: TimeoutException =>
        println("TIMEOUT!")
        slrun.destroy()
        slrun.exitValue()
    }
  }

  def sendSlave(dstIp: String, filename: String): Unit = {
    val res = cmd(
      Seq("scp",
          "-o",
          "StrictHostKeyChecking=no",
          filename,
          "hadoop@" + dstIp + ":" + filename))
    println(res)
  }

  def createDir(username: String): Unit = {
    Seq("mkdir", "-p", "/tmp/" + username + "/maps") ! ProcessLogger(_ => (),
                                                                     _ => ())
  }

  def umxToRmx(args: Array[String]) = {
    val key = args(0)
    val dstFile = args(1)
    val srcFiles = args.drop(2)
    val bw = new BufferedWriter(new FileWriter(dstFile))
    val res = srcFiles
      .flatMap(
        src => Source.fromFile(src).getLines().filter(l => l.startsWith(key))
      )
      .map(_.split(" "))
      .filter(_.length == 2)
      .map(spl => (spl(0), spl(1).toInt))
      .foldLeft((key, 0)) { case (acc, e) => (acc._1, acc._2 + e._2) }
    bw.write(s"${res._1} ${res._2}")
    println(s"${res._1} ${res._2}")
    bw.close
  }

  /*
      args(0) = 0 : Sx -> UMx
      args(0) = 1 : UMx -> SMx
   */
  override def main(args: Array[String]): Unit = {
    createDir("naegel_a")
    //Thread.sleep(10000)
    //println(exec)
    if (args.length == 0) return
    if (args(0) == "0") countWord_sToUm(args(1))
    else if (args(0) == "1") umxToRmx(args.drop(1))

  }
}
