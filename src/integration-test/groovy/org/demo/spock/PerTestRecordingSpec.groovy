/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.demo.spock

import grails.plugin.geb.ContainerGebSpec
import spock.lang.Shared


class PerTestRecordingSpec extends ContainerGebSpec {

    @Shared
    serverPort = 8080 // TODO: this needs to be a configuration

    void 'first test'() {
        when: 'visiting the grails home page'
        go('https://grails.apache.org/')

        then: 'the page loads correctly'
        title.contains('Apache Grails')
    }

    void 'second test'() {
        when: 'visiting the geb ome page'
        go('https://groovy.apache.org/geb/')

        // and: 'ensuring file size is different'
        // Thread.sleep(1000)

        then: 'the page loads correctly'
        title.contains('Geb - Very Groovy Browser Automation')
    }

    void 'verify last recording directory'() {
        when:
        // Logic from GrailsGebSettings
        String recordingDirectoryName = System.getProperty('grails.geb.recording.directory', 'build/gebContainer/recordings')
        File baseRecordingDir = new File(recordingDirectoryName)

        then: 'base recording directory should exist'
        baseRecordingDir.exists()

        when: 'get most recent recording directory'
        // Find the timestamped recording directory (should be the most recent one)
        File recordingDir = null
        File[] timestampedDirs = baseRecordingDir.listFiles({ File dir ->
            dir.isDirectory() && dir.name.matches('\\d{8}_\\d{6}')
        } as FileFilter)

        if (timestampedDirs && timestampedDirs.length > 0) {
            // Get the most recent directory
            recordingDir = timestampedDirs.sort { it.name }.last()
        }

        and: 'Get all recording files (mp4 or flv)'
        File[] recordingFiles = recordingDir?.listFiles({ File file ->
            file.isFile() && (file.name.endsWith('.mp4') || file.name.endsWith('.flv')) && file.name.contains(this.class.getSimpleName())
        } as FileFilter)

        then: 'recording directory should exist'
        recordingDir != null
        recordingDir.exists()

        and: 'recording files should be created for each test method'
        recordingFiles != null
        recordingFiles.length >= 2 // At least 2 files for the first two test methods

        and: 'recording files should have different content (different sizes)'
        // Sort by last modified time to get the most recent files
        File[] sortedFiles = recordingFiles.sort { it.lastModified() }
        File secondLastFile = sortedFiles[sortedFiles.length - 2]
        File lastFile = sortedFiles[sortedFiles.length - 1]

        // Files should have different sizes (allowing for small variations due to timing)
        long sizeDifference = Math.abs(lastFile.length() - secondLastFile.length())
        sizeDifference > 1000 // Expect at least 1KB difference
    }

    def cleanup() {
        sleep(1000) // give the last video time to copy
    }
}