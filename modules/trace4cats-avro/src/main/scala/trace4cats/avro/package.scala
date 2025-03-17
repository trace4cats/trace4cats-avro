package trace4cats

import scala.util.Try

package object avro {

  val AgentHostEnv = "T4C_AGENT_HOSTNAME"

  val AgentPortEnv = "T4C_AGENT_PORT"

  val CollectorHostEnv = "T4C_COLLECTOR_HOST"

  val CollectorPortEnv = "T4C_COLLECTOR_PORT"

  val DefaultHostname = "localhost"

  val DefaultPort = 7777

  def intEnv(key: String): Option[Int] = Option(System.getenv(key)).flatMap(v => Try(v.toInt).toOption)

  def agentHostname: String = Option(System.getenv(AgentHostEnv)).getOrElse(DefaultHostname)

  def agentPort: Int = intEnv(AgentPortEnv).getOrElse(DefaultPort)

}
