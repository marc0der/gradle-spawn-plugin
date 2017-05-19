##Gradle Spawn Plugin

This plugin is used for starting and stopping UNIX command line processes from within your build.

A typical application of this plugin is for stopping and starting an embedded web server when running functional tests from within your build.

###Setup

Add the following to your `build.gradle`:

    import com.wiredforcode.gradle.spawn.*

	buildscript {
		repositories {
			...
			maven { url 'http://dl.bintray.com/vermeulen-mp/gradle-plugins' }
		}
		dependencies {
			classpath 'com.wiredforcode:gradle-spawn-plugin:0.6.0'
		}
	}

	apply plugin: 'com.wiredforcode.spawn'

	task startServer(type: SpawnProcessTask, dependsOn: 'assemble') {
		command "java -jar ${projectDir}/build/libs/zim-service.jar"
		ready 'Started Application'
	}

	task stopServer(type: KillProcessTask)

The `startServer` task is used for starting the process.

The command line passed into the `command` method is typically a blocking process. The String passed into the `ready` method is the trigger used for continuing the build once the server process is up and running. If this String is discovered in the stdout stream of the server, the process will be placed into the background and the Gradle build will continue.

Once the build draws to a close, the `stopServer` task is then used to gracefully shut down the server process.

###PID File

The `SpawnProcessTask` will automatically deposit a `.pid.lock` file in the working directory. This contains the PID of the running process.
The `KillProcessTask` will read this lock file, kill the process gracefully, and remove the file.

It is also possible to set the name of the pid file allowing more than one process to be opened.

	task startServer(type: SpawnProcessTask, dependsOn: 'assemble') {
		command "java -jar ${projectDir}/build/libs/zim-service.jar"
		ready 'Started Application'
		pidLockFileName '.other.pid.lock'
	}

###Log File

The `SpawnProcessTask` works by directly reading the output stream of the child process it starts, looking for the ready statement. 
If you specify a `logFileName` it will instead route the output stream to that file and then tail that file looking for the ready statement.
This is very useful for long lived processes that generate lots of output and would otherwise deadlock (see http://stackoverflow.com/questions/3285408/java-processbuilder-resultant-process-hangs#answer-3285479)

The log file will be removed by the `SpawnProcessTask` if it exists before spawning the process, effectively truncating it.

	task startServer(type: SpawnProcessTask) {
		command "java -jar someScriptHere.sh"
		ready 'Started Application'
		pidLockFileName '.other.pid.lock'
		logFileName = 'myServerLog.txt'
	}