/*
 * Copyright 2010-2013 the original author or authors.
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

package griffon.core.resources.editors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;

/**
 * The PropertyEditorResolver can be used to locate a property editor for
 * any given type name. This property editor must support the
 * java.beans.PropertyEditor interface for editing a given object.
 * <p/>
 *
 * @author Andres Almiray
 * @since 1.3.0
 */
public final class PropertyEditorResolver {
    private static final Logger LOG = LoggerFactory.getLogger(PropertyEditorResolver.class);

    private PropertyEditorResolver() {

    }

    /**
     * Locate a value editor for a given target type.
     * <p/>
     * If the input {@code type} is an Enum then an instance of {@code EnumPropertyEditor}
     * is returned with the {@code type} set as {@code enumType}.
     *
     * @param type The Class object for the type to be edited
     * @return An editor object for the given target class.
     *         The result is null if no suitable editor can be found.
     * @see  griffon.core.resources.editors.EnumPropertyEditor
     */
    public static PropertyEditor findEditor(Class<?> type) {
        if (type == null) return null;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Searching PropertyEditor for " + type.getName());
        }

        PropertyEditor editor = null;
        if (Enum.class.isAssignableFrom(type)) {
            editor = new EnumPropertyEditor();
            ((EnumPropertyEditor) editor).setEnumType((Class<? extends Enum>) type);
        } else {
            editor = PropertyEditorManager.findEditor(type);
        }

        if (editor != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("PropertyEditor for " + type.getName() + " is " + editor.getClass().getName());
            }
        }
        return editor;
    }
}
