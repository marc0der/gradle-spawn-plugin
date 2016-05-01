package com.wiredforcode.gradle.spawn

import org.gradle.api.DefaultTask

class DefaultSpawnTask extends DefaultTask {

    private static final String PID_LOCK_SUFFIX = '.pid.lock'

    String spawnName
    String directory = '.'

    File getPidFile() {
        return new File(directory, '.' + spawnName + PID_LOCK_SUFFIX)
    }
}
