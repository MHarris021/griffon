/*
 * Copyright 2008-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static griffon.util.GriffonApplicationUtils.isMacOSX
import static griffon.util.GriffonApplicationUtils.isWindows
import static griffon.util.GriffonNameUtils.*

/**
 * Created by IntelliJ IDEA.
 * @author Danno.Ferrin
 * Date: Aug 5, 2008
 * Time: 10:35:06 PM
 */

includeTargets << griffonScript('Package')
includeTargets << griffonScript('_GriffonBootstrap')

target(name: 'runApp', description: "Runs the application from the command line", prehook: null, posthook: null) {
    if (isPluginProject) {
        println "Cannot run application: project is a plugin!"
        exit(1)
    }
    doRunApp()
}

target(name: 'doRunApp', description: "Runs the application from the command line", prehook: null, posthook: null) {
    depends(prepackage)

    // calculate the needed jars
    File jardir = new File(ant.antProject.replaceProperties(buildConfig.griffon.jars.destDir))
    // launch event after jardir has been defined
    event('RunAppTweak', [])

    runtimeJars = setupRuntimeJars()

    // setup the vm
    if (!binding.variables.javaVM) {
        def javaHome = ant.antProject.properties."environment.JAVA_HOME"
        javaVM = [javaHome, 'bin', 'java'].join(File.separator)
    }

    def jvmOpts = setupJvmOpts()
    def javaOpts = setupJavaOpts(true)
    if (argsMap.containsKey('debug')) {
        if (argsMap.debugPort != null) argsMap['debug-port'] = argsMap.debugPort
        if (argsMap.debugAddr != null) argsMap['debug-addr'] = argsMap.debugAddr
        if (argsMap.debugSuspend != null) argsMap['debug-suspend'] = argsMap.debugSuspend
        String portNum = argsMap['debug-port'] ?: '18290'  //default is 'Gr' in ASCII
        String addr = argsMap['debug-addr'] ?: '127.0.0.1'
        String debugSocket = ''

        if (portNum =~ /\d+/) {
            if (addr == '127.0.0.1') {
                debugSocket = ",address=$portNum"
            } else {
                debugSocket = ",address=$addr:$portNum"
            }
        }

        String debugSuspend = (argsMap['debug-suspend'] ?: 'n').toLowerCase()
        switch (debugSuspend) {
            case 'true':
            case 'y':
            case 'on':
            case 'yes':
                debugSuspend = 'y'
                println("Debugged process will start suspended :")
                println("   connect with a debugger compatible with the java debug wire protocol (jdwp) at address $addr:$portNum to resume it.");
                break
            case 'false':
            case 'n':
            case 'off':
            case 'no':
                debugSuspend = 'n'
                break
            default:
                println("Unrecognized value in '--debug-suspend=${argsMap.debugSuspend}' : must be 'y' or 'n'.")
                println("   Forcing to 'n', debugged process will not suspend waiting for a connection at start.")
                debugSuspend = 'n'
        }
        javaOpts << "-Xrunjdwp:transport=dt_socket$debugSocket,suspend=$debugSuspend,server=y"
    }
    if (buildConfig.griffon.memory?.min) {
        javaOpts << "-Xms$buildConfig.griffon.memory.min"
    }
    if (buildConfig.griffon.memory?.max) {
        javaOpts << "-Xmx$buildConfig.griffon.memory.max"
    }
    if (buildConfig.griffon.memory?.minPermSize && buildConfig.griffon.memory?.maxPermSize) {
        javaOpts << "-XX:MaxPermSize=$buildConfig.griffon.memory.maxPermSize"
        javaOpts << "-XX:PermSize=$buildConfig.griffon.memory.minPermSize"
    }
    if (isMacOSX) {
        javaOpts << "-Xdock:name=${griffonAppName}"
        javaOpts << "-Xdock:icon=${resolveApplicationIcnsFile().canonicalPath}"
    }

    debug("Running JVM options:")
    jvmOpts.each { debug("  $it") }
    javaOpts.each { debug("  $it") }

    sysProperties.'griffon.application.name' = griffonAppName
    List sysprops = []
    debug("System properties:")
    sysProperties.each { key, value ->
        if (null == value) return
        debug("  -D$key=${quote(value)}")
        sysprops << "-D${key}=${quote(value)}"
    }
    debug("Application arguments:")
    argsMap.each { k, v ->
        if (k == 'params' || k == 'clean') return
        debug('  --' + k + '=' + quote(String.valueOf(v)))
    }
    argsMap.params.each { debug("  ${it}") }

    def runtimeClasspath = runtimeJars.collect { f ->
        f.absolutePath.startsWith(jardir.absolutePath) ? f.absolutePath - jardir.absolutePath - File.separator : f
    }.join(File.pathSeparator)

    runtimeClasspath = [i18nDir, resourcesDir, runtimeClasspath, projectMainClassesDir].join(File.pathSeparator)
    runtimeClasspath = quote(runtimeClasspath)

    event 'StatusUpdate', ['Launching application']
    // start the process
    try {
        def cmd = [javaVM]
        // let's make sure no empty/null String is added
        jvmOpts.each { s -> if (s) cmd << s }
        javaOpts.each { s -> if (s) cmd << s }
        sysprops.each { s -> if (s) cmd << s }
        [proxySettings, '-classpath', runtimeClasspath, griffonApplicationClass].each { s -> if (s) cmd << s }
        // GRIFFON-574 - add all named params first
        argsMap.each { k, v ->
            if (k == 'params' || k == 'clean') return
            cmd << '--' + k + '=' + quote(String.valueOf(v))
        }
        argsMap.params.each { s -> cmd << s.trim() }
        if (isWindows) cmd = cmd.collect { it.replace('\\\\', '\\') }
        debug("Executing ${cmd.join(' ')}")
        Process p = Runtime.runtime.exec(isWindows? cmd.join(' ') : cmd as String[], null, jardir)

        // pipe the output
        p.consumeProcessOutput(System.out, System.err)

        // wait for it.... wait for it...
        p.waitFor()
    } finally {
// XXX -- NATIVE
        if (platformDir.exists()) {
            ant.delete(dir: platformDir)
        }
// XXX -- NATIVE
    }
}

setDefaultTarget(runApp)
