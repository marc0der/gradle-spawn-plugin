package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static com.wiredforcode.gradle.spawn.SpawnProcessTask.LOCK_FILE

class KillProcessTaskSpec extends Specification {

    static final SPAWN_PROCESS_TASK_NAME = "spawnProcess"
    static final KILL_PROCESS_TASK_NAME = "killProcess"

    Project project
    SpawnProcessTask spawnTask
    KillProcessTask killTask

    void setup(){
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'spawn'

        spawnTask = project.tasks.findByName(SPAWN_PROCESS_TASK_NAME)
        killTask = project.tasks.findByName(KILL_PROCESS_TASK_NAME)

    }

    void "should kill a process if a pid lock file is present"() {
        given:
        int kindaUnique = Math.random() * 10000
        def directory = new File("/tmp/spawn-$kindaUnique")
        directory.mkdirs()
        def directoryPath = directory.toString()

        and:
        def processSource = new File("src/test/resources/process.sh")
        def process = new File(directory, "process.sh")
        process << processSource.text
        process.setExecutable(true)

        and:
        spawnTask.command = "./process.sh"
        spawnTask.ready = "It is done..."
        spawnTask.directory = directoryPath

        and:
        killTask.directory = directoryPath

        when:
        spawnTask.spawn()
        def lockFile = new File(directoryPath, LOCK_FILE)

        then:
        lockFile.exists()

        when:
        killTask.kill()

        then:
        ! lockFile.exists()

        cleanup:
        assert directory.deleteDir()
    }

    void "should explode if no pid file exists"() {
        given:
        int kindaUnique = Math.random() * 10000
        def directory = new File("/tmp/spawn-$kindaUnique")
        directory.mkdirs()
        def directoryPath = directory.toString()

        and:
        killTask.directory = directoryPath
        def lockFile = new File(directoryPath, LOCK_FILE)

        when:
        killTask.kill()

        then:
        thrown GradleException
    }

    void "should enforce mandatory directory field"() {
        given:
        killTask.directory = null

        when:
        killTask.kill()

        then:
        thrown GradleException
    }

}
