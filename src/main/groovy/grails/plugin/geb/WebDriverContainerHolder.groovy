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
package grails.plugin.geb

import org.openqa.selenium.edge.EdgeOptions
import org.openqa.selenium.firefox.FirefoxOptions

import java.lang.reflect.Field
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import com.github.dockerjava.api.model.ContainerNetwork
import geb.Browser
import geb.Configuration
import geb.ConfigurationLoader
import geb.spock.SpockGebTestManagerBuilder
import geb.test.GebTestManager
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.PortForwardingContainer
import org.testcontainers.containers.VncRecordingContainer
import org.testcontainers.images.PullPolicy

import grails.plugin.geb.serviceloader.ServiceRegistry

/**
 * Responsible for initializing a {@link org.testcontainers.containers.BrowserWebDriverContainer BrowserWebDriverContainer}
 * per the Spec's {@link grails.plugin.geb.ContainerGebConfiguration ContainerGebConfiguration}.  This class will try to
 * reuse the same container if the configuration matches the current container.
 *
 * @author James Daugherty
 * @since 4.1
 */
@Slf4j
@CompileStatic
class WebDriverContainerHolder {

    /* Driver defaults to Firefox due to availability on AMD64 and ARM64 platforms.
    * {@link https://github.com/SeleniumHQ/docker-selenium?tab=readme-ov-file#experimental-multi-arch-amd64aarch64armhf-images Selenium Browser Archetectures}
    */

    private static final String DEFAULT_HOSTNAME_FROM_HOST = 'localhost'

    GrailsGebSettings grailsGebSettings
    GebTestManager testManager
    Browser currentBrowser
    BrowserWebDriverContainer currentContainer
    WebDriverContainerConfiguration currentConfiguration

    WebDriverContainerHolder(GrailsGebSettings grailsGebSettings) {
        this.grailsGebSettings = grailsGebSettings
    }

    boolean isInitialized() {
        currentContainer != null
    }

    void stop() {
        currentContainer?.stop()
        currentContainer = null
        currentBrowser = null
        testManager = null
        currentConfiguration = null
    }

    boolean matchesCurrentContainerConfiguration(WebDriverContainerConfiguration specConfiguration) {
        specConfiguration == currentConfiguration && grailsGebSettings.recordingMode == BrowserWebDriverContainer.VncRecordingMode.SKIP
    }

    /**
     * Returns a host port to use for the container in this order:
     * Class field 'hostPort' if found in the invocation (Spec).
     * A 'hostPort' property in the GebConfig configuration.
     * @param invocation
     * @return
     */
    private static int getPort(IMethodInvocation invocation) {
        // use the test class hostPort if specified
        try {
            return (int) invocation.instance.metaClass.getProperty(invocation.instance, 'hostPort')
        } catch (ignored) {
            println "no hostPort from invocation"
        }

        try {
            // use geb config setting or default 8080
            Configuration gebConfig = new ConfigurationLoader().conf
            return (int) gebConfig.rawConfig.getProperty('hostPort')
        } catch (ignored) {
            println "no hostPort in config"
        }

        println "using default hostPort 8080"
        return 8080
    }

    @PackageScope
    boolean reinitialize(IMethodInvocation invocation) {
        WebDriverContainerConfiguration specConfiguration = new WebDriverContainerConfiguration(
                invocation.getSpec()
        )
        if (matchesCurrentContainerConfiguration(specConfiguration)) {
            return false
        }

        if (initialized) {
            stop()
        }

        currentConfiguration = specConfiguration
        currentContainer = new BrowserWebDriverContainer().withRecordingMode(
                grailsGebSettings.recordingMode,
                grailsGebSettings.recordingDirectory,
                grailsGebSettings.recordingFormat
        )

        currentContainer.with {
            withEnv('SE_ENABLE_TRACING', grailsGebSettings.tracingEnabled)
            withAccessToHost(true)
            withImagePullPolicy(PullPolicy.ageBased(Duration.of(1, ChronoUnit.DAYS)))
            // start()  // without Capabilities this is starting chrome
        }

        // Create a Geb Configuration the same way as an empty Browser constructor would do
        Configuration gebConfig = new ConfigurationLoader().conf
        String env = System.getProperty('geb.env') ?: 'firefox'

        ChromeOptions chromeOptions
        FirefoxOptions firefoxOptions
        EdgeOptions edgeOptions


        switch (env) {
            case 'chrome':
                Map prefs = [
                        "credentials_enable_service"             : false,
                        "profile.password_manager_enabled"       : false,
                        "profile.password_manager_leak_detection": false
                ]
                chromeOptions = new ChromeOptions()
                // TODO: guest would be preferred, but this causes issues with downloads
                // see https://issues.chromium.org/issues/42323769
                // chromeOptions.addArguments("--guest")
                chromeOptions.setExperimentalOption("prefs", prefs)
                currentContainer.withCapabilities(chromeOptions)
                break

            case 'edge':
                edgeOptions = new EdgeOptions()
                currentContainer.withCapabilities(edgeOptions)
                break

            case 'firefox':
                firefoxOptions = new FirefoxOptions()
                currentContainer.withCapabilities(firefoxOptions)
                break

            default:
                firefoxOptions = new FirefoxOptions()
                currentContainer.withCapabilities(firefoxOptions)
        }

        currentContainer.start()



        if (hostnameChanged) {
            currentContainer.execInContainer('/bin/sh', '-c', "echo '$hostIp\t${currentConfiguration.hostName}' | sudo tee -a /etc/hosts")
        }

        // Create a Geb Configuration the same way as an empty Browser constructor would do
        // Configuration gebConfig = new ConfigurationLoader().conf

        // Ensure driver points to re-initialized container with correct host
        // Driver is explicitly quit by us in stop() method to fulfill our resulting responsibility
        gebConfig.cacheDriver = false

        // "If driver caching is disabled then this setting defaults to true" - we override to false
        gebConfig.quitDriverOnBrowserReset = false

        gebConfig.baseUrl = currentContainer.seleniumAddress.toString()
        if (currentConfiguration.reporting) {
            gebConfig.reportsDir = grailsGebSettings.reportingDirectory
            gebConfig.reporter = (invocation.sharedInstance as ContainerGebSpec).createReporter()
        }

        if (gebConfig.driverConf instanceof RemoteWebDriver) {
            // Similar to Browser#getDriverConf's Exception
            throw new IllegalStateException(
                    "The 'driver' config value is an instance of RemoteWebDriver. " +
                            'You need to wrap the driver instance in a closure.'
            )
        }
        if (gebConfig.driverConf == null) {
            // If no driver was set in GebConfig.groovy, default to Firefox
            gebConfig.driverConf = { ->

                log.info('Using default Firefox RemoteWebDriver for {}', currentContainer.seleniumAddress)
                new RemoteWebDriver(currentContainer.seleniumAddress, new FirefoxOptions())
            }
        }

        // If `GebConfig` instantiates a `RemoteWebDriver` without using it's `remoteAddress` constructor,
        // the `RemoteWebDriver` will be instantiated using the `webdriver.remote.server` system property.
        String existingPropertyValue = System.getProperty('webdriver.remote.server')
        System.setProperty('webdriver.remote.server', currentContainer.seleniumAddress.toString())
        gebConfig.driver // This will implicitly call `createDriver()`

        // Restore the `webdriver.remote.server` system property
        if (existingPropertyValue == null) {
            System.clearProperty('webdriver.remote.server')
        } else {
            System.setProperty('webdriver.remote.server', existingPropertyValue)
        }

        currentBrowser = new Browser(gebConfig)

        if (currentConfiguration.fileDetector != NullContainerFileDetector) {
            ServiceRegistry.setInstance(ContainerFileDetector, currentConfiguration.fileDetector)
        }
        ContainerFileDetector fileDetector = ServiceRegistry.getInstance(ContainerFileDetector, DefaultContainerFileDetector)
        ((RemoteWebDriver) currentBrowser.driver).setFileDetector(fileDetector)

        // Overwrite `GebConfig` timeouts with values explicitly set in `GrailsGebSettings` (via system properties)
        if (grailsGebSettings.implicitlyWait != GrailsGebSettings.DEFAULT_TIMEOUT_IMPLICITLY_WAIT)
            currentBrowser.driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(grailsGebSettings.implicitlyWait))
        if (grailsGebSettings.pageLoadTimeout != GrailsGebSettings.DEFAULT_TIMEOUT_PAGE_LOAD)
            currentBrowser.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(grailsGebSettings.pageLoadTimeout))
        if (grailsGebSettings.scriptTimeout != GrailsGebSettings.DEFAULT_TIMEOUT_SCRIPT)
            currentBrowser.driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(grailsGebSettings.scriptTimeout))

        // There's a bit of a chicken and egg problem here: the container & browser are initialized when
        // the static/shared fields are initialized, which is before the grails server has started so the
        // real url cannot be set (it will be checked as part of the geb test manager startup in reporting mode)
        // set the url to localhost, which the selenium server should respond to (albeit with an error that will be ignored)

        currentBrowser.baseUrl = 'http://localhost'

        testManager = createTestManager()

        return true
    }

    void setupBrowserUrl(IMethodInvocation invocation) {
        if (!currentBrowser) {
            return
        }
        int port = getPort(invocation)
        Testcontainers.exposeHostPorts(port)

        currentBrowser.baseUrl = "${currentConfiguration.protocol}://${currentConfiguration.hostName}:${port}"
    }

    private GebTestManager createTestManager() {
        new SpockGebTestManagerBuilder()
                .withReportingEnabled(currentConfiguration.reporting)
                .withBrowserCreator(
                        new Supplier<Browser>() {
                            @Override
                            Browser get() {
                                currentBrowser
                            }
                        }
                )
                .build()
    }

    private boolean getHostnameChanged() {
        currentConfiguration.hostName != ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    private static String getHostIp() {
        try {
            PortForwardingContainer.getDeclaredMethod('getNetwork').with {
                accessible = true
                Optional<ContainerNetwork> network = invoke(PortForwardingContainer.INSTANCE) as Optional<ContainerNetwork>
                return network.get().ipAddress
            }
        } catch (Exception e) {
            throw new RuntimeException('Could not access network from PortForwardingContainer', e)
        }
    }

    /**
     * Returns the hostname that the server under test is available on from the host.
     * <p>This is useful when using any of the {@code download*()} methods as they will connect from the host,
     * and not from within the container.
     * <p>Defaults to {@code localhost}. If the value returned by {@code webDriverContainer.getHost()}
     * is different from the default, this method will return the same value same as {@code webDriverContainer.getHost()}.
     *
     * @return the hostname for accessing the server under test from the host
     */
    String getHostNameFromHost() {
        return hostNameChanged ? currentContainer.host : DEFAULT_HOSTNAME_FROM_HOST
    }

    private boolean isHostNameChanged() {
        return currentContainer.host != ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    @CompileStatic
    @EqualsAndHashCode
    private static class WebDriverContainerConfiguration {

        String protocol
        String hostName
        boolean reporting
        Class<? extends ContainerFileDetector> fileDetector

        WebDriverContainerConfiguration(SpecInfo spec) {
            ContainerGebConfiguration configuration

            // Check if the class implements the interface
            if (IContainerGebConfiguration.isAssignableFrom(spec.reflection)) {
                configuration = spec.reflection.getConstructor().newInstance() as ContainerGebConfiguration
            } else {
                // Check for the annotation
                configuration = spec.annotations.find {
                    it.annotationType() == ContainerGebConfiguration
                } as ContainerGebConfiguration
            }

            protocol = configuration?.protocol() ?: ContainerGebConfiguration.DEFAULT_PROTOCOL
            hostName = configuration?.hostName() ?: ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
            reporting = configuration?.reporting() ?: false
            fileDetector = configuration?.fileDetector() ?: ContainerGebConfiguration.DEFAULT_FILE_DETECTOR
        }
    }

    /**
     * Workaround for https://github.com/testcontainers/testcontainers-java/issues/3998
     * Restarts the VNC recording container to enable separate recording files for each test method.
     * This method uses reflection to access the VNC recording container field in BrowserWebDriverContainer.
     * Should be called BEFORE each test starts.
     */
    void restartVncRecordingContainer() {
        if (!grailsGebSettings.recordingEnabled || !grailsGebSettings.restartRecordingContainerPerTest || !currentContainer) {
            return
        }
        try {
            // Use reflection to access the VNC recording container field
            Field vncRecordingContainerField = BrowserWebDriverContainer.getDeclaredField('vncRecordingContainer')
            vncRecordingContainerField.setAccessible(true)

            VncRecordingContainer vncContainer = vncRecordingContainerField.get(currentContainer) as VncRecordingContainer

            if (vncContainer) {
                // Stop the current VNC recording container
                vncContainer.stop()
                // Create and start a new VNC recording container for the next test
                VncRecordingContainer newVncContainer = new VncRecordingContainer(currentContainer)
                        .withVncPassword('secret')
                        .withVncPort(5900)
                        .withVideoFormat(grailsGebSettings.recordingFormat)
                vncRecordingContainerField.set(currentContainer, newVncContainer)
                newVncContainer.start()

                log.debug('Successfully restarted VNC recording container')
            }
        } catch (Exception e) {
            log.warn("Failed to restart VNC recording container: ${e.message}", e)
            // Don't throw the exception to avoid breaking the test execution
        }
    }
}
