/*
 * Copyright 2009-2013 the original author or authors.
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

package org.codehaus.griffon.cli;

/**
 * Exception thrown when a script exits, but Griffon has disabled the
 * system exit. Used for testing and Griffon interactive mode.
 */
public class ScriptExitException extends RuntimeException {
    private int exitCode;

    public ScriptExitException(int exitCode) { 
        this.exitCode = exitCode;
    }

    public ScriptExitException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public ScriptExitException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public ScriptExitException(int exitCode, Throwable cause) {
        super(cause);
        this.exitCode = exitCode;
    }

    public int getExitCode() { return exitCode; }
}

