package com.suikwsha.akka.sample.cluster

import akka.actor._
import scala.swing._
import java.awt.Color
import com.typesafe.config.ConfigFactory
import scala.collection.{mutable, JavaConversions}
import akka.cluster.{Member, Cluster}
import akka.cluster.ClusterEvent.{MemberRemoved, UnreachableMember, MemberUp, ClusterDomainEvent}
import scala.collection.mutable.ListBuffer
import javax.swing.event.{ListDataEvent, ListDataListener}
import scala.swing.event.ListChanged


/**
 * Created with IntelliJ IDEA.
 * User: suikwasha
 * Date: 2013/11/03
 * Time: 15:06
 * To change this template use File | Settings | File Templates.
 */
object AkkaClusterSample {


  def main(args: Array[String]): Unit = {
    val Array(remoteaddr, remoteport, localport, screenName) =
      if(args.length == 4) {
        args
      } else {
        Array("127.0.0.1", "2552", "2552", "noname")
      }

    val configMap: Map[String, _ <: AnyRef] = Map[String, String](
      "akka.actor.provider" -> "akka.cluster.ClusterActorRefProvider",
      "akka.remote.log-remote-lifecycle-events" -> "off",
      "akka.cluster.auto-down" -> "on",
      "akka.remote.netty.tcp.hostname" -> "127.0.0.1",
      "akka.remote.netty.tcp.port" -> localport
    )

    val members = new mutable.HashSet[Member] with mutable.SynchronizedSet[Member]
    val chatLog = new ListView[String](Seq.empty[String]) {
      border = Swing.TitledBorder(Swing.LineBorder(Color.BLACK), "chat log")
    }
    val clusterLog = new ListView[String](Seq.empty[String]) {
      border = Swing.TitledBorder(Swing.LineBorder(Color.BLACK), "cluster log")
    }

    val config = ConfigFactory.parseMap(JavaConversions.mapAsJavaMap(configMap))
    val system = ActorSystem("ClusterSystem", config)

    val clusterListener = system.actorOf(Props(classOf[ClusterListenerActor], clusterLog, members), name = "clusterListener")
    val echoActor = system.actorOf(Props(classOf[EchoActor], chatLog, members), name = "echoActor")
    val cluster = Cluster(system)
    cluster.join(Address("akka.tcp", "ClusterSystem", remoteaddr, remoteport.toInt))
    cluster.subscribe(clusterListener, classOf[ClusterDomainEvent])

    val app = new SimpleSwingApplication {
      def top = new MainFrame {
        title = "Akka Cluster Sample"
        contents = new BorderPanel {
          import BorderPanel.Position._
          add(new SplitPane(Orientation.Horizontal) {
            topComponent = new ScrollPane(chatLog)
            bottomComponent = new ScrollPane(clusterLog)
            dividerLocation = 250
          }, Center)
          add(new BorderPanel {
            border = Swing.TitledBorder(Swing.LineBorder(Color.BLACK), "input")
            val messageField = new TextField
            add(messageField, Center)
            add(new Button("send") {
              action = Action("send") {
                echoActor ! SendMessage(Message(screenName, messageField.text))
              }
            }, East)
          }, South)
        }
      }
    }
    app.main(args)
  }

}

class ClusterListenerActor(val view: ListView[String], val members: mutable.HashSet[Member]) extends Actor {

  def receive = {
    case MemberUp(member) => {
      view.listData = (s"Member is Up: ${member}") +: view.listData
      members.add(member)
    }
    case UnreachableMember(member) => view.listData = (s"Member detected as unreachable: ${member}") +: view.listData
    case MemberRemoved(member, stat) => {
      view.listData = s"Member removed: ${member} after ${stat}" +: view.listData
      members.remove(member)
    }
    case _ =>
  }
}

case class Message(sender: String, message: String)
case class SendMessage(message: Message)

class EchoActor(val view: ListView[String], val members: mutable.HashSet[Member]) extends Actor with ActorLogging {
  def receive = {
    case SendMessage(message) => members.foreach { member =>
      context.actorSelection(RootActorPath(member.address) / "user" / "echoActor") ! message
    }
    case Message(sender, message) => {
      view.listData = (s"(${sender}) ${message}") +: view.listData
      log.info(s"received chat message from ${sender}, message = ${message}")
    }
    case _ =>
  }
}
