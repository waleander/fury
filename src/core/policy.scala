/*
   ╔═══════════════════════════════════════════════════════════════════════════════════════════════════════════╗
   ║ Fury, version 0.8.0. Copyright 2018-20 Jon Pretty, Propensive OÜ.                                         ║
   ║                                                                                                           ║
   ║ The primary distribution site is: https://propensive.com/                                                 ║
   ║                                                                                                           ║
   ║ Licensed under  the Apache License,  Version 2.0 (the  "License"); you  may not use  this file  except in ║
   ║ compliance with the License. You may obtain a copy of the License at                                      ║
   ║                                                                                                           ║
   ║     http://www.apache.org/licenses/LICENSE-2.0                                                            ║
   ║                                                                                                           ║
   ║ Unless required  by applicable law  or agreed to in  writing, software  distributed under the  License is ║
   ║ distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. ║
   ║ See the License for the specific language governing permissions and limitations under the License.        ║
   ╚═══════════════════════════════════════════════════════════════════════════════════════════════════════════╝
*/
package fury.core

import fury.ogdl._, fury.io._, fury.strings._, fury.model._

import scala.collection.immutable._
import scala.util._

object Policy {
  def read(implicit log: Log): Policy =
    Ogdl.read[Policy](Installation.policyFile,
        upgrade(_)).toOption.getOrElse(Policy(SortedSet.empty[Grant]))

  def save(policy: Policy)(implicit log: Log): Try[Unit] =
    Installation.policyFile.extantParents().writeSync(Ogdl.serialize(Ogdl(policy)))

  private def upgrade(ogdl: Ogdl)(implicit log: Log): Ogdl = ogdl
}

case class Policy(policy: SortedSet[Grant] = TreeSet()) {
  def forContext(layout: Layout, projectId: ProjectId/*, layer: Layer*/): Policy =
    Policy(policy.filter {
      case Grant(DirectoryScope(dir), _) => dir == layout.baseDir.value
      case Grant(ProjectScope(id), _)    => projectId == id
      //case Grant(LayerScope(hash), _)    => hash == layer.hash
    })

  def grant(scope: Scope, permissions: List[Permission]): Policy =
    copy(policy = policy ++ permissions.map(Grant(scope, _)))

  def obviate(scope: Scope, permissions: List[Permission]): Policy =
    copy(policy = policy.filterNot(permissions.contains))

  def checkAll(permissions: Iterable[Permission]): Try[Unit] = {
    val missing = permissions.to[Set] -- policy.map(_.permission)
    if(missing.isEmpty) Success(()) else Failure(NoPermissions(missing))
  }

  def save(file: Path): Try[Unit] = file.writeSync {
    val sb = new StringBuilder()
    sb.append("grant {\n")
    policy.foreach { grant =>
      val p = grant.permission
      val actionAddendum = p.action.fold("") { a => s""", "$a"""" }
      sb.append(str""" permission ${p.className} "${p.target}"${actionAddendum};""")
      sb.append('\n')
    }
    sb.append("};\n")
    sb.toString
  }
}
  