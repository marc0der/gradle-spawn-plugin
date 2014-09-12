package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static com.wiredforcode.gradle.spawn.SpawnProcessTask.LOCK_FILE

class SpawnProcessTaskSpec extends Specification {

    static final SPAWN_PROCESS_TASK_NAME = 'spawnProcess'

    Project project
    SpawnProcessTask task

    File directory

    void setup(){
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.wiredforcode.spawn'

        task = project.tasks.findByName(SPAWN_PROCESS_TASK_NAME)

        int kindaUnique = Math.random() * 10000
        directory = new File("/tmp/spawn-$kindaUnique")
        directory.mkdirs()
    }

    void cleanup(){
        assert directory.deleteDir()
    }

    void "should start a new process and place token pid lock file in current directory"(){
        given:
        def command = './process.sh'
        def ready = 'It is done...'

        and:
        def processSource = new File("src/test/resources/process.sh")
        def process = new File(directory, "process.sh")
        process << processSource.text
        process.setExecutable(true)

        and:
        task.command = command
        task.ready = ready
        task.directory = directory.toString()

        when:
        task.spawn()

        then:
        new File(directory, LOCK_FILE).exists()
    }

    void "should check if pid file already exists"() {
        given:
        task.directory = directory.toString()

        and:
        new File(directory, LOCK_FILE).createNewFile()

        when:
        task.spawn()

        then:
        thrown GradleException
    }

    void "should enforce mandatory command field"() {
        given:
        task.directory = directory.toString()
        task.ready = "Some message"
        task.command = null

        when:
        task.spawn()

        then:
        thrown GradleException
    }

    void "should enforce mandatory ready field"() {
        given:
        task.directory = directory.toString()
        task.command = "ls -lah"
        task.ready = null

        when:
        task.spawn()

        then:
        thrown GradleException
    }

    void "should set current directory as default for directory field"() {
        given:
        task

        expect:
        task.directory == '.'
    }

    void "should allow an override of the directory field"() {
        given:
        task

        when:
        task.directory = directory.toString()

        then:
        task.directory == directory.toString()
    }


}
