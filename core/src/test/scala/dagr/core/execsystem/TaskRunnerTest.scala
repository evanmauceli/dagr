/*
 * The MIT License
 *
 * Copyright (c) 2015 Fulcrum Genomics LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package dagr.core.execsystem

import java.nio.file.{Files, Path}

import dagr.core.tasksystem._
import dagr.core.util.{LazyLogging, UnitSpec}
import org.scalatest.{OptionValues, PrivateMethodTester}

class TaskRunnerTest extends UnitSpec with PrivateMethodTester with OptionValues with LazyLogging {

  /********************************************
  * Simple Tasks, both in Process and in Jvm
  *********************************************/

  private def setupTaskList(argv: List[String], taskId: BigInt = 1): (UnitTask, BigInt, TaskExecutionInfo) = {
    // setup
    val script: Path = Files.createTempFile("TaskRunnerTest", ".sh")
    val logFile: Path = Files.createTempFile("TaskRunnerTest", ".log")
    val task: UnitTask = new ShellCommand(argv:_*).withName(argv.mkString(" ")).getTasks.head
    val taskInfo: TaskExecutionInfo = new TaskExecutionInfo(task = task,
      id = taskId,
      status = TaskStatus.UNKNOWN,
      submissionDate = None,
      startDate = None,
      endDate = None,
      attemptIndex = 1,
      script = script,
      logFile = logFile)
    (task, taskId, taskInfo)
  }

  private class TrivialInJvmTask(exitCode: Int) extends InJvmTask with FixedResources {
    override def inJvmMethod(): Int = exitCode
  }

  private def setupInJvmTaskList(exitCode: Int, taskId: BigInt = 1): (UnitTask, BigInt, TaskExecutionInfo) = {
    // setup
    val script: Path = Files.createTempFile("TaskRunnerTest", ".sh")
    val logFile: Path = Files.createTempFile("TaskRunnerTest", ".log")
    val task: UnitTask = new TrivialInJvmTask(exitCode = exitCode).getTasks.head
    val taskInfo: TaskExecutionInfo = new TaskExecutionInfo(task = task,
      id = taskId,
      status = TaskStatus.UNKNOWN,
      submissionDate = None,
      startDate = None,
      endDate = None,
      attemptIndex = 1,
      script = script,
      logFile = logFile)
    (task, taskId, taskInfo)
  }

  private def setupTask(task: UnitTask, taskId: BigInt = 1): (UnitTask, BigInt, TaskExecutionInfo) = {
    // setup
    val script: Path = Files.createTempFile("TaskRunnerTest", ".sh")
    val logFile: Path = Files.createTempFile("TaskRunnerTest", ".log")
    val newTask = task.getTasks.head
    val taskInfo: TaskExecutionInfo = new TaskExecutionInfo(task = newTask,
      id = taskId,
      status = TaskStatus.UNKNOWN,
      submissionDate = None,
      startDate = None,
      endDate = None,
      attemptIndex = 1,
      script = script,
      logFile = logFile)
    (newTask, taskId, taskInfo)
  }

  private def getCompletedTask(taskRunner: TaskRunner, task: UnitTask, taskId: BigInt, taskInfo: TaskExecutionInfo, taskStatus: TaskStatus.Value, exitCode: Int = 0, onCompleteSuccessful: Boolean = true, failedAreCompleted: Boolean = true): Unit = {
    // get the completed task
    val completedTasks: Map[BigInt, (Int, Boolean)] = taskRunner.getCompletedTasks(timeout = 1000, failedAreCompleted = failedAreCompleted)
    completedTasks should have size 1 // only one task right?
    completedTasks should contain key taskId // should contain the task id
    completedTasks.get(taskId).value._1 should be (exitCode) // exit code is zero
    completedTasks.get(taskId).value._2 should be (onCompleteSuccessful) // onComplete was successful should be true
    taskInfo.status should be (taskStatus)
  }

  private def runTaskAndComplete(task: UnitTask,
                                 taskId: BigInt,
                                 taskInfo: TaskExecutionInfo,
                                 taskStatus: TaskStatus.Value,
                                 exitCode: Int = 0,
                                 onCompleteSuccessful: Boolean = true,
                                 failedAreCompleted: Boolean = true): Unit = {
    val taskRunner: TaskRunner = new TaskRunner()

    // run the task
    taskRunner.runTask(taskInfo = taskInfo)
    taskRunner.getRunningTasks should have size 1 // there should be a running task

    // get the completed task
    getCompletedTask(taskRunner = taskRunner, task = task, taskId = taskId, taskInfo = taskInfo, taskStatus = taskStatus, exitCode = exitCode, onCompleteSuccessful = onCompleteSuccessful, failedAreCompleted = failedAreCompleted)
  }

  "TaskRunner" should "run a simple echo command" in {
    val (task, taskId, taskInfo) = setupTaskList(List[String]("echo", "Hello world"), 1)

    runTaskAndComplete(task = task, taskId = taskId, taskInfo = taskInfo, taskStatus = TaskStatus.SUCCEEDED, exitCode = 0, onCompleteSuccessful = true)
  }

  def runSimpleExitTest(inJvmTask: Boolean, exitSuccessfully: Boolean, onCompleteSuccessful: Boolean, failedAreCompleted: Boolean) {
    val exitCode: Int = if (exitSuccessfully) 0 else 1
    val (task: UnitTask, taskId: BigInt, taskInfo: TaskExecutionInfo) = if (inJvmTask) {
      setupInJvmTaskList(exitCode = exitCode, taskId = 1)
    }
    else {
      setupTaskList(List[String]("exit", exitCode.toString), taskId = 1)
    }

    val expectedStatus: TaskStatus.Value = (exitSuccessfully, failedAreCompleted) match {
      case (true, _) => TaskStatus.SUCCEEDED
      case (false, true) => TaskStatus.SUCCEEDED
      case (false, false) => TaskStatus.FAILED_COMMAND
    }

    runTaskAndComplete(task = task, taskId = taskId, taskInfo = taskInfo, taskStatus = expectedStatus, exitCode = exitCode, onCompleteSuccessful = onCompleteSuccessful, failedAreCompleted = failedAreCompleted)
  }

  it should "run exit 0 and succeed" in {
    for (inJvmTask <- List(true, false)) {
      for (failedAreCompleted <- List(true, false)) {
        runSimpleExitTest(inJvmTask = inJvmTask, exitSuccessfully = true, onCompleteSuccessful = true, failedAreCompleted = failedAreCompleted)
      }
    }
  }

  it should "run exit 1 and fail" in {
    for (inJvmTask <- List(true, false)) {
      runSimpleExitTest(inJvmTask = inJvmTask, exitSuccessfully = false, onCompleteSuccessful = true, failedAreCompleted = false)
    }
  }

  it should "run exit 1 and fail but treated as completed" in {
    for (inJvmTask <- List(true, false)) {
      runSimpleExitTest(inJvmTask = inJvmTask, exitSuccessfully = false, onCompleteSuccessful = true, failedAreCompleted = true)
    }
  }

  it should "terminate a sleeping thread" in {
    val (task, taskId, taskInfo) = setupTaskList(List[String]("sleep", "1000"), 1)
    val taskRunner: TaskRunner = new TaskRunner()

    taskRunner.runTask(taskInfo = taskInfo)
    taskRunner.getRunningTasks should have size (1) // there should be a running task

    // terminate it
    taskRunner.terminateTask(taskId = taskId) should be (true)

    // get the completed tasks
    getCompletedTask(taskRunner = taskRunner, task = task, taskId = taskId, taskInfo = taskInfo, taskStatus = TaskStatus.FAILED_COMMAND, exitCode = 1, onCompleteSuccessful = true, failedAreCompleted = false)
  }

  /********************************************
  * onComplete success and failure
  *********************************************/

  private trait FailOnCompleteTask extends UnitTask {
    override def onComplete(exitCode: Int): Boolean = false
  }

  private trait OnCompleteIsOppositeExitCodeTask extends UnitTask {
    override def onComplete(exitCode: Int): Boolean = 0 != exitCode
  }

  it should "fail a task when its onComplete method returns false" in {
    val (taskOne, taskOneId, taskOneInfo) = setupTask(task = new ShellCommand("exit 0")  with FailOnCompleteTask withName "Dummy One", taskId = 1)
    val (taskTwo, taskTwoId, taskTwoInfo) = setupTask(task = new ShellCommand("exit 1") with FailOnCompleteTask withName "Dummy Two", taskId = 2)

    runTaskAndComplete(task = taskOne, taskId = taskOneId, taskInfo = taskOneInfo, taskStatus = TaskStatus.FAILED_ON_COMPLETE, exitCode = 0, onCompleteSuccessful = false, failedAreCompleted = false)
    runTaskAndComplete(task = taskTwo, taskId = taskTwoId, taskInfo = taskTwoInfo, taskStatus = TaskStatus.FAILED_COMMAND, exitCode = 1, onCompleteSuccessful = false, failedAreCompleted = false)
  }

  it should "have a task that fails its onComplete method if the exit code is zero" in {
    val (taskOne, taskOneId, taskOneInfo) = setupTask(task = new ShellCommand("exit 0") with OnCompleteIsOppositeExitCodeTask withName "Dummy One", taskId = 1)
    val (taskTwo, taskTwoId, taskTwoInfo) = setupTask(task = new ShellCommand("exit 1") with OnCompleteIsOppositeExitCodeTask withName "Dummy Two", taskId = 2)

    runTaskAndComplete(task = taskOne, taskId = taskOneId, taskInfo = taskOneInfo, taskStatus = TaskStatus.FAILED_ON_COMPLETE, exitCode = 0, onCompleteSuccessful = false, failedAreCompleted = false)
    runTaskAndComplete(task = taskTwo, taskId = taskTwoId, taskInfo = taskTwoInfo, taskStatus = TaskStatus.FAILED_COMMAND, exitCode = 1, onCompleteSuccessful = true, failedAreCompleted = false)
  }

  it should "have None for onCompleteSuccessful before the task has finished" in {
    val (task, taskId, taskInfo) = setupTaskList(List[String]("sleep", "1000"), 1)
    val taskRunner: TaskRunner = new TaskRunner()

    taskRunner.runTask(taskInfo = taskInfo)
    val runningTasks: List[BigInt] = taskRunner.getRunningTasks.toList
    runningTasks should have size 1 // there should be a running task
    runningTasks.head should be (taskId)
    taskInfo.status should be (TaskStatus.STARTED)
    val privateMethod: PrivateMethod[Option[Boolean]] = PrivateMethod[Option[Boolean]]('getOnCompleteSuccessful)
    val onCompleteSuccesful: Option[Boolean] = taskRunner invokePrivate privateMethod(taskId)
    onCompleteSuccesful should be ('empty)

    // the rest of the tests after this are just to make sure things work out and that we do not have a zombie process

    // terminate it
    taskRunner.terminateTask(taskId = taskId) should be (true)

    // get the completed tasks
    getCompletedTask(taskRunner = taskRunner, task = task, taskId = taskId, taskInfo = taskInfo, taskStatus = TaskStatus.FAILED_COMMAND, exitCode = 1, onCompleteSuccessful = true, failedAreCompleted = false)
  }

  class ProcessBuilderExceptionTask extends ProcessTask with FixedResources {
    override def getProcessBuilder(script: Path, logFile: Path, setPipefail: Boolean = true): scala.sys.process.ProcessBuilder = {
      throw new IllegalStateException("I failed creating my process builder")
    }
    override def onComplete(exitCode: Int): Boolean = false

    /**
      * Abstract method that must be implemented by child classes to return a list or similar traversable
      * list of command line elements (command name and arguments) that form the command line to be run.
      * Individual values will be converted to Strings before being used by calling toString.
      */
    override def args = List.empty
  }

  it should "get a non-zero exit code for a task that fails during getProcessBuilder by throwing an exception" in {
    val taskRunner: TaskRunner = new TaskRunner()
    val task: ProcessBuilderExceptionTask = new ProcessBuilderExceptionTask()
    val taskInfo: TaskExecutionInfo = new TaskExecutionInfo(task = task,
      id = 1,
      status = TaskStatus.UNKNOWN,
      submissionDate = None,
      startDate = None,
      resources = ResourceSet.empty,
      endDate = None,
      attemptIndex = 1,
      script = null,
      logFile = null)
    taskRunner.runTask(taskInfo)
    taskInfo.status should be(TaskStatus.STARTED)
    val completedTasks: Map[BigInt, (Int, Boolean)] = taskRunner.getCompletedTasks()
    completedTasks.get(1).value._1 should be(1) // exit code
    completedTasks.get(1).value._2 should be(false) // on complete
  }
}