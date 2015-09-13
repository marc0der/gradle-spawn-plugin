package com.wiredforcode.gradle.spawn

import org.gradle.api.DefaultTask

class DefaultSpawnTask extends DefaultTask {
    String pidLockFileName = '.pid.lock'
    String directory = '.'

    File getPidFile() {
        return new File(directory, pidLockFileName)
    }
}
