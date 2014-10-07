package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class KillProcessTaskSpec extends Specification {

    static final SPAWN_PROCESS_TASK_NAME = "spawnProcess"
    static final KILL_PROCESS_TASK_NAME = "killProcess"

    Project project
    SpawnProcessTask spawnTask
    KillProcessTask killTask

    File directory

    void setup(){
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.wiredforcode.spawn'

        spawnTask = project.tasks.findByName(SPAWN_PROCESS_TASK_NAME)
        killTask = project.tasks.findByName(KILL_PROCESS_TASK_NAME)

        int kindaUnique = Math.random() * 10000
        directory = new File("/tmp/spawn-$kindaUnique")
        directory.mkdirs()
    }

    void "should kill a process if a pid lock file is present"() {
        given:
        def directoryPath = directory.toString()
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
        def lockFile = new File(directoryPath, spawnTask.lockFile)

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
        def directoryPath = directory.toString()

        and:
        killTask.directory = directoryPath

        when:
        killTask.kill()

        then:
        thrown GradleException
    }

    void "should set current directory as default for directory field"() {
        given:
        killTask

        expect:
        killTask.directory == '.'
    }

    void "should allow an override of the directory field"() {
        given:
        def directoryPath = directory.toString()

        and:
        killTask

        when:
        killTask.directory = directoryPath

        then:
        killTask.directory == directoryPath
    }

}
