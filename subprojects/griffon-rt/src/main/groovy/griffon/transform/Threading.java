/*
 * Copyright 2009-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package griffon.transform;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Annotates a method or property.</p>
 *
 * Annotated elements must follow these rules
 * <ul>
 * <li>must be a public method or property</li>
 * <li>name does not match an event handler</li>
 * <li>must pass {@code griffon.util.GriffonClassUtils.isPlainMethod()} if it's a method</li>
 * <li>its value must be a closure (including curried method pointers) if it's a property</li>
 * </ul>
 *
 * This annotation takes {@code griffon.util.Threading.Policy} as value, with {@code Threading.Policy.OUTSIDE_UITHREAD} being
 * the default value.<p>
 *
 * <p>The following snippet exemplifies the compactness of code when the annotation is applied </p>
 * <pre>
 * import griffon.transform.Threading
 *
 * class Sample {
 *     &#064;Threading
 *     void doSomethingOutside(String arg) {
 *         println "Outside $arg"
 *     }
 *     &#064;Threading(Threading.Policy.INSIDE_UITHREAD_SYNC)
 *     void doSomethingInside(String arg) {
 *         println "Inside $arg"
 *     }
 * }
 * </pre>
 *
 * <p>The equivalent, non-annotated code is</p>
 * <pre>
 * import griffon.core.UIThreadManager
 *
 * class Sample {
 *     void doSomethingOutside(String arg) {
 *         UIThreadManager.instance.executeOutside {
 *             println "Outside $arg"
 *         }
 *     }
 *     void doSomethingInside(String arg) {
 *         UIThreadManager.instance.executeSync {
 *             println "Inside $arg"
 *         }
 *     }
 * }
 * </pre>
 *
 * @author Andres Almiray
 * @see Threading.Policy
 * @see org.codehaus.griffon.ast.ThreadingASTTransformation
 * @since 0.9.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@GroovyASTTransformationClass("org.codehaus.griffon.ast.ThreadingASTTransformation")
public @interface Threading {
    Policy value() default Policy.OUTSIDE_UITHREAD;

    /**
     * Indicates the type of threading management for a method or property.</p>
     * The following values apply
     * <ul>
     * <li>{@code SKIP} - no threading management will be performed.</li>
     * <li>{@code OUTSIDE_UITHREAD} - code should be invoked outside of the UI thread.</li>
     * <li>{@code INSIDE_UITHREAD_SYNC} - code should be invoked inside the UI thread using a synchronous call.</li>
     * <li>{@code INSIDE_UITHREAD_ASYNC} - code should be invoked inside the UI thread using an asynchronous call.</li>
     * </ul>
     *
     * @author Andres Almiray
     * @see Threading
     * @since 0.9.2
     */
    public enum Policy {
        /**
         * Skip threading injection
         */
        SKIP,
        /**
         * Inject execOutside wrapper
         */
        OUTSIDE_UITHREAD,
        /**
         * Inject execSync wrapper
         */
        INSIDE_UITHREAD_SYNC,
        /**
         * Inject execAsync wrapper
         */
        INSIDE_UITHREAD_ASYNC;
    }
}
