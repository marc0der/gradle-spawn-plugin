package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static com.wiredforcode.gradle.spawn.SpawnProcessTask.LOCK_FILE

class KillProcessTaskSpec extends Specification {
    Project project
    SpawnProcessTask spawnTask
    KillProcessTask killTask

    File directory

    void setup(){
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.wiredforcode.spawn'

        spawnTask = project.tasks.findByName("spawnProcess")
        killTask = project.tasks.findByName("killProcess")

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
        def lockFile = spawnTask.pidFile

        then:
        lockFile.exists()

        when:
        killTask.kill()

        then:
        !lockFile.exists()

        cleanup:
        assert directory.deleteDir()
    }

    void "should exit gracefully, if the server is not running"() {
        given:
        def directoryPath = directory.toString()

        and:
        killTask.directory = directoryPath

        when:
        killTask.kill()

        then:
        noExceptionThrown()
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

    void "should allow an override of the pid file name"() {
        given:
        killTask

        when:
        killTask.pidLockFileName = '.new.pid.lock'

        then:
        killTask.pidLockFileName == '.new.pid.lock'
    }
}
