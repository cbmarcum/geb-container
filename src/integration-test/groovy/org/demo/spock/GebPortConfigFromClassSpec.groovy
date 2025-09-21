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

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.SimpleFileServer
import com.sun.net.httpserver.SimpleFileServer.OutputLevel
import grails.plugin.geb.ContainerGebSpec
import spock.lang.Narrative

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Test spec to verify that the correct port configuration is being used.
 * Some reference taken from:
 * groovy-geb/module/geb-core/src/test/groovy/geb/conf/ConfigurationLoaderSpec.groovy
 */
@Narrative("To verify that class field overrides config file.")
class GebPortConfigFromClassSpec extends ContainerGebSpec {

    static HttpServer server

    int hostPort = 8000

    def "should use the configuration port"() {
        given: "a server listening on port 8000"
        startServer(8000)

        when: "go to localhost"
        go "/" // browser is going to 8080

        then: "the page title should be correct"
        title == "Hello Geb"

        and: "the welcome header should be displayed"
        $("h1").text() == "Welcome to the Geb/Spock Test"

    }



    def cleanup() {
        sleep(1000) // give the last video time to copy
        stopServer(0)
    }

    void startServer(int port) {
        println "Starting JWebServer on port $port..."
        // def staticDir = new File('src/integration-test/resources/static').toPath()
        URL staticDirUrl = getClass().getResource("/static")
        assert staticDirUrl != null

        // Convert the URL to a URI and then to a Path
        Path staticDirPath = Paths.get(staticDirUrl.toURI())
        def addr = new InetSocketAddress(port)

        // Use JDK's built-in SimpleFileServer to serve the static content
        server = SimpleFileServer.createFileServer(addr, staticDirPath, OutputLevel.NONE)
        server.start()
        println "JWebServer started on port $port"
    }

    void stopServer(int delay) {
        println "Stopping JWebServer..."
        if (server) {
            server.stop(delay)
            println "JWebServer stopped"
        } else {
            println "JWebServer "
        }
    }

}
