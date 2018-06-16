import scala.concurrent._
import scala.io.Source
import ExecutionContext.Implicits.global
import scala.sys.process._
import java.io._
//import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object Master extends App {

  def cmd(commande: Seq[String]) = {
    val slrun = commande.run
    //val slrun = commande.lineStream_! //ProcessLogger(line => ())
    val f = Future(blocking(slrun.exitValue())) // wrap in Future
    try {
      Await.result(f, duration.Duration(10, "sec"))
    } catch {
      case _: TimeoutException =>
        println("TIMEOUT!")
        slrun.destroy()
        slrun.exitValue()
      case _ => slrun.exitValue()
    }
  }

  def future_filt_map(commands: List[(String, Seq[String])]): List[String] = {
    Await
      .result(Future.sequence(
                commands
                  .map(ip_cmd => Future((ip_cmd._1, cmd(ip_cmd._2))))
              ),
              duration.Duration(15, "sec"))
      .filter(_._2 == 0)
      .map(_._1)
  }

  def check_ip(list_ip: List[String]): List[String] = {
    println("check working ip adress...")
    future_filt_map(
      list_ip.map(
        ip =>
          (ip, Seq("ssh", "-i", "~/.ssh/hadoop_tp", "hadoop@" + ip, "hostname"))
      )
    )
  }

  def makeSplits(ipList: List[String]): List[String] = {
    future_filt_map(
      ipList.map(
        ip =>
          (ip,
           Seq("ssh",
               "-i",
               "~/.ssh/hadoop_tp",
               "hadoop@" + ip,
               "mkdir",
               "-p",
               "/tmp/naegel_a/splits"))
      )
    )
  }

  //def recursiveListFiles(f: File): Array[File] = {
  //  val these = f.listFiles
  //  these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  //}

  def sendSx(listIp: List[String]): Array[(String, String)] = {
    var i_file = 0
    var i_ip = 0
    var file_to_ip = Array[(String, String)]()
    println("\nsending sx to differents machine...")
    val fileList =
      new File("/tmp/naegel_a/splits").listFiles().map(f => f.getAbsolutePath)
    while (i_file < fileList.length) {
      val res = cmd(
        Seq("scp",
            "-i",
            "~/.ssh/hadoop_tp",
            fileList(i_file),
            "hadoop@" + listIp(i_ip) + ":/tmp/naegel_a/splits"))
      if (res == 0) {
        file_to_ip = file_to_ip :+ ("UM" + i_file.toString, listIp(i_ip))
        i_file += 1
      }
      i_ip = (i_ip + 1) % listIp.size
    }
    file_to_ip foreach { tp =>
      println(tp._1 + " - " + tp._2)
    }

    file_to_ip
  }

  def execSlave(file_to_ip: Array[(String, String)]): List[(String, String)] = {
    println("\ncount words on slaves...")
    val future_res = Await
      .result(
        Future.sequence(
          file_to_ip.toList.map { tp =>
            val filenumber = ("""\d+""".r findAllIn tp._1).toList.head

            Future {
              (cmdGetOutput(
                 Seq("ssh",
                     "-i",
                     "~/.ssh/hadoop_tp",
                     "hadoop@" + tp._2,
                     "java",
                     "-jar",
                     "/tmp/naegel_a/slave.jar",
                     "0",
                     "/tmp/naegel_a/splits/S" + filenumber + ".txt")),
               tp._1)
            }
          }
        ),
        duration.Duration(20, "seconds")
      )
      .filter(prStr => prStr._1._1 == 0)
      .map(tupls => (tupls._1._2, tupls._2))
      .flatMap(tup => tup._1.split(" ").map((_, tup._2)))
    //Await.result(Future.sequence(future_res), duration.Duration(20, "seconds"))
    future_res foreach (tp => println(tp._1 + " " + tp._2))
    future_res
  }

  def streamResMap(
      um_array: List[(String, String)]): Map[String, List[String]] = {
    val mapRes = um_array
      .groupBy(_._1)
      .mapValues(v =>
        v map { vv =>
          vv._2
      })
    for ((k, v) <- mapRes) {
      printf("key: %s , values [", k)
      for (va <- v) {
        printf("%s, ", va)
      }
      printf("]\n")
    }
    mapRes
  }

  case class Dispach(word: String, umx: List[String], n: Int, ip: String)

  /*
  map: word -> List(UMx)
  file_to_ip: (umx, ip)
  availableIp: List(ip)
   */
  def dispachSlave(mapRes: Map[String, List[String]],
                   fileToIp: Array[(String, String)],
                   availableIp: List[String]) = {
    def _getIpFromUm: Array[(String, String)] => String => String =
      fti => um => fti.filter(tupl => tupl._1 == um)(0)._2
    val getIpFromUm = _getIpFromUm(fileToIp)
    def sendSlaveToSlave(umx: String, src: String, dst: String) = {
      println(s"$umx: $src -> $dst")
      cmd(
        Seq(
          "scp",
          "-3",
          "hadoop@" + src + ":/tmp/naegel_a/maps/" + umx + ".txt",
          "hadoop@" + dst + ":/tmp/naegel_a/maps/" + umx + ".txt"
        )
      )
    }

    println("\nDispach UMx to slave...")

    val disp = mapRes.keys zip mapRes.values zip (Stream from 0) map (kvi =>
      Dispach(kvi._1._1,
              kvi._1._2,
              kvi._2,
              availableIp(kvi._2 % availableIp.size)))
    val dispList = disp.toSeq.toList
    val groupSend = dispList
      .map(dis => (dis.ip, dis.umx))
      .groupBy(_._1)
      .mapValues(_.map(_._2).flatMap(x => x).distinct)
      .toList
      .flatMap(kv => kv._2.map(v => (kv._1, v)))
    //val toto = dispList.map(dis => (dis.ip, dis.umx)).groupBy(_._1).mapValues(_.map(_._2).flatMap(x=>x).distinct)//.toList.flatMap(kv => kv._2.map(v => (kv._1, v)))
    Await
      .result(
        Future.sequence(
          groupSend.map(
            ipUm =>
              Future(
                sendSlaveToSlave(ipUm._2, getIpFromUm(ipUm._2), ipUm._1)
            ))
        ),
        duration.Duration(15, "sec")
      )
    dispList
  }

  def cmdGetOutput(commande: Seq[String]) = {
    val out = new StringBuilder()
    val proc = ProcessLogger(line => out.append(line + " "), line => ())
    val slrun = commande.run(proc)
    //val slrun = commande.lineStream_! //ProcessLogger(line => ())
    val f = Future(blocking(slrun.exitValue())) // wrap in Future
    try {
      (Await.result(f, duration.Duration(10, "sec")), out.toString())
    } catch {
      case _: TimeoutException =>
        println("TIMEOUT!")
        slrun.destroy()
        (slrun.exitValue(), "")
      case _ => (slrun.exitValue(), "")
    }
  }

  def slaveSM(dispL: List[Master.Dispach]) = {
    Await
      .result(
        Future.sequence(
          dispL.map(
            dispach =>
              Future(
                (dispach,
                 cmdGetOutput(Seq(
                   "ssh",
                   "-i",
                   "~/.ssh/hadoop_tp",
                   "hadoop@" + dispach.ip,
                   "java",
                   "-jar",
                   "/tmp/naegel_a/slave.jar",
                   "1",
                   dispach.word,
                   s"/tmp/naegel_a/reduces/RM${dispach.n}.txt"
                 ) ++
                   dispach.umx.map("/tmp/naegel_a/maps/" + _ + ".txt")))
            ))
        ),
        duration.Duration(15, "sec")
      )
      .filter(_._2._1 == 0)
      .map(dispOut => (dispOut._1, dispOut._2._2))
  }

  def processRes(res: List[(Dispach, String)]) = {
    val bw = new BufferedWriter(new FileWriter("res.txt"))
    res.foreach { r =>
      bw.write(r._2 + "\n")
      println(r._2)
    }
    bw.close
  }

  override def main(args: Array[String]): Unit = {
    //println(cmd(Seq("rveve")))
    val list_ip = Source.fromResource("ip_list.txt").getLines.toList
    val checked_ip = check_ip(list_ip)
    val ipWithSplits = makeSplits(checked_ip)
    val fileToIp = sendSx(ipWithSplits)
    val resSlave = execSlave(fileToIp)
    val mapRes = streamResMap(resSlave)
    println("phase de MAP terminée")
    val dispachList = dispachSlave(mapRes, fileToIp, ipWithSplits)
    val res = slaveSM(dispachList)
    processRes(res)
  }
}
