import org.jgroups.{Message, ReceiverAdapter, JChannel}

/**
  * Created by mike on 20.12.15.
  */
object Main {
  def main(args: Array[String]): Unit = {
    val channel = new JChannel("mping.xml")
    channel.setReceiver(new ReceiverAdapter() {
      override def receive(msg: Message) = {
        System.out.println(s"Received message from ${msg.getSrc}: ${msg.getObject}")
      }
    })

    channel.connect("MyCluster")

  }
}
