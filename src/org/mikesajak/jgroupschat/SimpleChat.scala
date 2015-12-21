package org.mikesajak.jgroupschat

import java.io._

import org.jgroups.util.Util
import org.jgroups.{View, ReceiverAdapter, Message, JChannel}
import org.mikesajak.jgroupschat.ClusterState.{ClusterStarted, ClusterStopped}

import scala.annotation.tailrec

object SimpleChat {
  def main(args: Array[String]): Unit = {
    new SimpleChat().start()
  }
}

sealed trait ClusterState
object ClusterState {
  case object ClusterStopped  extends ClusterState
  case object ClusterStarting extends ClusterState
  case object ClusterStarted  extends ClusterState
}

class SimpleChat extends ReceiverAdapter {
  var channel: JChannel = _
  val userName = System.getProperty("user.name", "n/a")
  var clusterState: ClusterState = ClusterStopped
  val stateMutex = new Object()

  def start() = {
    channel = new JChannel()
    channel.connect("ChatCluster")
    channel.setReceiver(this)
    channel.getState(null, 10000)
    eventLoop()
    channel.close()
  }

  private def isClusterController() =
    channel.getAddress == channel.getView.getMembers.get(0)

  private def eventLoop() = {
    val in = new BufferedReader(new InputStreamReader(System.in))
    var quit = false

    initialization()

    chatLoop()
  }

  @tailrec
  private def chatLoop(): Unit = {
    val in = new BufferedReader(new InputStreamReader(System.in))
    print(">")
    System.out.flush()
    val line = in.readLine().toLowerCase()
    val quit = line.startsWith("quit") || line.startsWith("exit")

    if (!quit) {
      val msgBody = s"[$userName] $line"
      val msg = new Message(null, null, msgBody)
      channel.send(msg)
      chatLoop()
    }
  }

  @tailrec
  private def initialization(): Unit = {
    try {
      if (isClusterController()) {
        val INIT_TIME = 20
        println(s"I'm the cluster controller. Simulate system initialization ${INIT_TIME}s")
        for (i <- INIT_TIME to 0 by -1) {
          print(s"$i..")
          Thread.sleep(1000)
        }
        stateMutex.synchronized{
          clusterState = ClusterStarted
        }
        println(s"Cluster initialized")
      } else {
        while (stateMutex.synchronized {clusterState != ClusterStarted}) {
          println(s"Waiting for cluster initialization, state=$clusterState")
          channel.getState(null, 10000)
          Thread.sleep(1000)
        }

        println(s"Cluster initialized! Moving on...")
      }
    } catch {
      case e: Exception =>
        println(s"Exception during getting state from cluster: $e")
        initialization()
    }
  }

  override def viewAccepted(view: View) = {
    println(s"** view: $view")
    if (isClusterController())
      println("   - I'm the cluster controller.")
  }

  override def receive(msg: Message) =
    println(s"${msg.getSrc}: ${msg.getObject}")

  override def getState(output: OutputStream) = {
    stateMutex.synchronized {
      Util.objectToStream(clusterState, new DataOutputStream(output))
    }
  }

  override def setState(input: InputStream) = {
    val newState = Util.objectFromStream(new DataInputStream(input)).asInstanceOf[ClusterState]
    stateMutex.synchronized {
      clusterState = newState
    }
    println(s"Received new state: $newState")
  }
}
