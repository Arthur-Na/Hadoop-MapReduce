import scala.sys.process._
import scala.io.Source
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.Try

object Deploy extends App {
  //val dep = Seq("deploy/src/main/resources/script.sh",
  //              "deploy/src/main/resources/ip_list.txt").!

  def cmd(commande: Seq[String]) = {
    val slrun = commande.run //lineStream_! ProcessLogger(line => ())
    val f = Future(blocking(slrun.exitValue())) // wrap in Future
    try {
      Await.result(f, duration.Duration(5, "sec"))
    } catch {
      case _: TimeoutException =>
        println(commande.mkString(" ") + " TIMEOUT!")
        slrun.destroy()
        slrun.exitValue()
    }
  }

  val list_ip = Source.fromResource("ip_list.txt").getLines.toList
  list_ip.foreach(println)

  println("check working ip adress...")
  val working_ip = list_ip.filter(
    ip =>
      cmd(Seq("ssh", "-i", "~/.ssh/hadoop_tp", "hadoop@" + ip, "hostname")) == 0
  )

  println("scp slave.jar...")
  val ip_with_jar = working_ip.filter(
    ip =>
      cmd(
        Seq("ssh",
            "-i",
            "~/.ssh/hadoop_tp",
            "hadoop@" + ip,
            "mkdir",
            "-p",
            "/tmp/naegel_a")) == 0 &&
        cmd(
          Seq("scp",
              "-i",
              "~/.ssh/hadoop_tp",
              "slave/target/scala-2.12/slave.jar",
              "hadoop@" + ip + ":/tmp/naegel_a")) == 0
  )
  ip_with_jar.foreach(println)

  /*println("cleaning dir...")
  working_ip.foreach(
    ip =>
      cmd(
        Seq("ssh",
            "-i",
            "~/.ssh/hadoop_tp",
            "hadoop@" + ip,
            "rm",
            "-rf",
            "/tmp/naegel_a/splits",
            "/tmp/naegel_a/maps",
            "/tmp/naegel_a/reduces"))
  )*/

  println("create splits dir...")
  val ip_with_splits = working_ip.filter(
    ip =>
      cmd(
        Seq("ssh",
            "-i",
            "~/.ssh/hadoop_tp",
            "hadoop@" + ip,
            "mkdir",
            "-p",
            "/tmp/naegel_a/splits",
            "/tmp/naegel_a/maps",
            "/tmp/naegel_a/reduces")) == 0
  )

}
