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
import spock.lang.Stepwise

@Stepwise // execute feature methods in declared order
class PerTestRecordingSpec extends ContainerGebSpec {

    // @Shared
    // serverPort = 8080 // TODO: this needs to be a configuration

    void '(setup) running a test to create a recording'() {
        when: 'visiting the grails home page'
        go('https://grails.apache.org/')

        then: 'the page loads correctly'
        title.contains('Apache Grails')
    }

    void '(setup) running a second test to create another recording'() {
        when: 'visiting the geb home page'
        go('https://groovy.apache.org/geb/')

        then: 'the page loads correctly'
        title.contains('Geb - Very Groovy Browser Automation')
    }

    void 'the recordings of the previous two tests are different'() {
        when: 'getting the configured base recording directory'
        // Logic from GrailsGebSettings
        def recordingDirectoryName = System.getProperty(
                'grails.geb.recording.directory',
                'build/gebContainer/recordings'
        )
        def baseRecordingDir = new File(recordingDirectoryName)

        then: 'base recording directory should exist'
        baseRecordingDir.exists()

        when: 'getting the most recent recording directory'
        // Find the timestamped recording directory (should be the most recent one)
        File recordingDir = null
        def timestampedDirs = baseRecordingDir.listFiles({ File dir ->
            dir.isDirectory() && dir.name ==~ /^\d{8}_\d{6}$/
        } as FileFilter)

        if (timestampedDirs) {
            // Get the most recent directory
            recordingDir = timestampedDirs.sort { it.name }.last()
        }

        then: 'the recording directory should be found'
        recordingDir != null

        when: 'getting all video recording files (mp4 or flv) from the recording directory'
        def recordingFiles = recordingDir?.listFiles({ File file ->
            isVideoFile(file) && file.name.contains(this.class.simpleName)
        } as FileFilter)

        then: 'recording files should exist for each test method'
        recordingFiles != null
        recordingFiles.length >= 2 // At least 2 files for the first two test methods

        and: 'the recording files should have different content (different sizes)'
        // Sort by last modified time to get the most recent files
        def sortedFiles = recordingFiles.sort { it.lastModified() }
        def secondLastFile = sortedFiles[sortedFiles.length - 2]
        def lastFile = sortedFiles[sortedFiles.length - 1]

        // Files should have different sizes (allowing for small variations due to timing)
        long sizeDifference = Math.abs(lastFile.length() - secondLastFile.length())
        sizeDifference > 2 // in KB
    }

    def cleanup() {
        sleep(1000) // give the last video time to copy
    }

    private static boolean isVideoFile(File file) {
        return file.isFile() && (file.name.endsWith('.mp4') || file.name.endsWith('.flv'))
    }

}