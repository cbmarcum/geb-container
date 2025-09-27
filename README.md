<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Geb Container Library

A library to support using Testcontainers in Apache Geb integration testing.

## Geb Functional Testing using Testcontainers

This library integrates [Geb](https://groovy.apache.org/geb/) with [Testcontainers](https://testcontainers.com/) to make it easy to write functional tests for your applications and utilize browsers running in testcontainers and optionally record the browser using a VNC testcontainer and/or capture reporting screenshots and HTML.

## Origins

Much of this library was derived from the [Apache Grails - Grails-Geb module](https://github.com/apache/grails-core/tree/HEAD/grails-geb) which is used to test Grails applications with Geb using Testcontainers.

We have modified the library to be able to test any web application running on localhost, not just a Grails application.

The library contains class names and configuration settings that contain `Grails`.  We are leaving those as-is for now.  That may change in major version updates.

## Examples

We have a [sample project](https://github.com/cbmarcum/geb-container-sample) using this library and this project has integration tests as well.

For further reference please see the [Geb documentation](https://groovy.apache.org/geb/).

## Usage

To use the library, add the following dependencies to your `build.gradle` file (adjust for the source set you're using it in, e.g., testImplementation or integrationTestImplementation.):
```groovy
dependencies {
    implementation "net.codebuilders:geb-container:<latest release>"
}
```

There are two ways to use this library. Either extend your test classes with the `ContainerGebSpec` class or with the `GebSpec` class.

### ContainerGebSpec (recommended)

By extending your test classes with `ContainerGebSpec`, your tests will automatically use a containerized browser using [Testcontainers](https://java.testcontainers.org/).
This requires a [compatible container runtime](https://java.testcontainers.org/supported_docker_environment/) to be installed, such as:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [OrbStack](https://orbstack.dev/) - macOS only
- [Rancher Desktop](https://rancherdesktop.io/)
- [podman desktop](https://podman-desktop.io/)
- [Colima](https://github.com/abiosoft/colima) - macOS and Linux

If you choose to use the `ContainerGebSpec` class, as long as you have a compatible container runtime installed, you don't need to do anything else.
Just run `./gradlew <your test task with ContainerGebSpec tests>` and a container will be started and configured to start a browser that can access your application under test. 

Without any additional configuration you will get a `Firefox` browser container testing a base URL of `http://localhost` on port `8080`. Firefox was chosen for [docker-selenium compatibility](https://github.com/SeleniumHQ/docker-selenium?tab=readme-ov-file#experimental-multi-arch-amd64aarch64armhf-images]) with x86_64 and aarch64 architectures.

#### Browser Container

The default container browser is `Firefox`. To configure a different browser you can specify a `geb.env` variable to the integrationTest task in Gradle:

```shell
./gradlew integrationTest -Dgeb.env=chrome
```

Reference the [geb-container-sample GebConfig.groovy](https://github.com/cbmarcum/geb-container-sample/blob/main/app/src/integration-test/resources/GebConfig.groovy) on how to setup the environment configuration.


#### Parallel Execution

Parallel execution of `ContainerGebSpec` specifications is not currently supported.

#### Custom Host Configuration

The annotation `ContainerGebConfiguration` exists to customize the connection the container will use to access the application under test.
The annotation is not required and `ContainerGebSpec` will use the default values in this annotation if it's not present.

The interface `IContainerGebConfiguration` exists as an inheritable version of the annotation.

#### Reporting (Screenshots and HTML)

To configure reporting, enable it using the `recording` property on the annotation `ContainerGebConfiguration`.  The following system properties exist for reporting configuration:

* `grails.geb.reporting.directory`
  * purpose: if the test enables reporting, the directory to save the reports relative to the project directory
  * defaults to `build/gebContainer/reports`

#### Recording

By default, no test recording will be performed.  Various system properties exist to change the recording behavior.  To set them, you can set them in your `build.gradle` file like so:

```groovy
tasks.withType(Test).configureEach {
    useJUnitPlatform()
    systemProperty('grails.geb.recording.mode', 'RECORD_ALL')
}
```

* `grails.geb.recording.mode`
  * purpose: which tests to record
  * possible values: `SKIP`, `RECORD_ALL`, or `RECORD_FAILING`
  * defaults to `SKIP`


* `grails.geb.recording.directory`
    * purpose: the directory to save the recordings relative to the project directory
    * defaults to `build/gebContainer/recordings`


* `grails.geb.recording.format`
    * purpose: sets the format of the recording
    * possible values are `FLV` or `MP4`
    * defaults to `MP4`

#### Uploads

Uploading a file is more complicated for Remote WebDriver sessions because the file you want to upload
is likely on the host executing the tests and not in the container running the browser.
For this reason, this plugin will setup a Local File Detector by default.

To customize the default, either:

1. Create a class that implements [`ContainerFileDetector`](./src/testFixtures/groovy/grails/plugin/geb/ContainerFileDetector.groovy)
   and specify its fully qualified class name in a `META-INF/services/grails.plugin.geb.ContainerFileDetector` file
   on the classpath (e.g., `src/integration-test/resources`).
2. Use the `ContainerGebConfiguration` annotation and set its `fileDetector` property to your `ContainerFileDetector` implementation class.

[//]: # (3. Call [`ServiceRegistry.setInstance&#40;&#41;`]&#40;./src/testFixtures/groovy/grails/plugin/geb/serviceloader/ServiceRegistry.groovy&#41;)
[//]: # (   in a Spock `setupSpec&#40;&#41;` method to apply your naming convention &#40;And use a `cleanupSpec&#40;&#41;` to limit this to one class&#41;.)

Alternatively, you can access the `BrowserWebDriverContainer` instance via
the `container` from within your `ContainerGebSpec` to, for example, call `.copyFileToContainer()`.
An Example of this can be seen in [ContainerSupport#createFileInputSource utility method](./src/testFixtures/groovy/grails/plugin/geb/support/ContainerSupport.groovy).

#### Timeouts

* `grails.geb.timeouts.implicitlyWait`
  * purpose: amount of time the driver should wait when searching for an element if it is not immediately present.
  * defaults to `0` seconds, which means that if an element is not found, it will immediately return an error.
  * Warning: Do not mix implicit and explicit waits. Doing so can cause unpredictable wait times.
    Consult the [Geb](https://groovy.apache.org/geb/manual/current/#implicit-assertions-waiting) 
    and/or [Selenium](https://www.selenium.dev/documentation/webdriver/waits/) documentation for details.
* `grails.geb.timeouts.pageLoad`
  * purpose: amount of time to wait for a page load to complete before throwing an error.
  * defaults to `300` seconds
* `grails.geb.timeouts.script`
  * purpose: amount of time to wait for an asynchronous script to finish execution before throwing an error.
  * defaults to `30` seconds

#### Observability and Tracing
Selenium integrates with [OpenTelemetry](https://opentelemetry.io) to support observability and tracing out of the box. By default, Selenium [enables tracing](https://www.selenium.dev/blog/2021/selenium-4-observability).

This plugin, however, **disables tracing by default** since most setups lack an OpenTelemetry collector to process the traces.

To enable tracing, set the following system property:
* `grails.geb.tracing.enabled`
  * possible values are `true` or `false`
  * defaults to `false`
  
This allows you to opt in to tracing when an OpenTelemetry collector is available.
