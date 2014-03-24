package com.wiredforcode.gradle.spawn

import org.gradle.api.Plugin
import org.gradle.api.Project


class SpawnPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.task('spawnProcess', type: SpawnProcessTask)
        project.task('killProcess', type: KillProcessTask)
    }
}
