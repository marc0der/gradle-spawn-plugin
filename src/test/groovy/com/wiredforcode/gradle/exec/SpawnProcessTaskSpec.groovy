package com.wiredforcode.gradle.exec

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static com.wiredforcode.gradle.exec.SpawnProcessTask.LOCK_FILE

class SpawnProcessTaskSpec extends Specification {

    static final SPAWN_PROCESS_TASK_NAME = 'spawnProcess'

    Project project
    SpawnProcessTask task

    void setup(){
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'spawn'

        task = project.tasks.findByName(SPAWN_PROCESS_TASK_NAME)
    }

    void "should start a new process and place token pid lock file in current directory"(){
        given:
        def command = './process.sh'
        def ready = 'It is done...'

        and:
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
        task.command = command
        task.ready = ready
        task.directory = directoryPath

        when:
        task.spawn()

        then:
        new File(directory, LOCK_FILE).exists()

        cleanup:
        assert directory.deleteDir()
    }

    void "should check if pid file already exists"() {
        given:
        int kindaUnique = Math.random() * 10000
        def directory = new File("/tmp/spawn-$kindaUnique")
        directory.mkdirs()

        and:
        def directoryPath = directory.toString()
        task.directory = directoryPath

        and:
        new File(directoryPath, LOCK_FILE).createNewFile()

        when:
        task.spawn()

        then:
        thrown GradleException
    }

}
