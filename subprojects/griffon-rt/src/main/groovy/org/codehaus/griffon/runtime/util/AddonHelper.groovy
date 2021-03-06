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

package org.codehaus.griffon.runtime.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import griffon.util.ApplicationClassLoader
import griffon.util.GriffonNameUtils
import groovy.transform.Synchronized
import org.codehaus.griffon.runtime.builder.CompositeBuilderHelper
import org.codehaus.griffon.runtime.builder.UberBuilder
import org.codehaus.griffon.runtime.core.DefaultGriffonAddon
import org.codehaus.griffon.runtime.core.DefaultGriffonAddonDescriptor
import griffon.core.*

import static griffon.util.GriffonClassUtils.getGetterName
import static griffon.util.GriffonClassUtils.getSetterName
import static griffon.util.GriffonNameUtils.getClassNameForLowerCaseHyphenSeparatedName

/**
 * Helper class for dealing with addon initialization.
 *
 * @author Danno Ferrin
 * @author Andres Almiray
 */
class AddonHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AddonHelper)

    private static final Map<String, Map<String, Object>> ADDON_CACHE = [:]

    static final DELEGATE_TYPES = Collections.unmodifiableList([
            "attributeDelegates",
            "preInstantiateDelegates",
            "postInstantiateDelegates",
            "postNodeCompletionDelegates"
    ])

    @Synchronized
    private static Map<String, Map<String, Object>> getAddonCache() {
        ADDON_CACHE
    }

    @Synchronized
    private static void computeAddonCache(GriffonApplication app) {
        if (!ADDON_CACHE.isEmpty()) return

        // Load addons in order
        URL addonMetadata = ApplicationClassLoader.get().getResource('META-INF/griffon-addons.properties')
        if (addonMetadata) {
            addonMetadata.text.eachLine { line ->
                String[] parts = line.split('=')
                String pluginName = parts[0].trim()
                ADDON_CACHE[pluginName] = [
                        node: null,
                        version: parts[1].trim(),
                        prefix: '',
                        name: pluginName,
                        className: getClassNameForLowerCaseHyphenSeparatedName(pluginName) + 'GriffonAddon'
                ]
            }
        }

        for (node in app.builderConfig) {
            String nodeName = node.key
            switch (nodeName) {
                case 'addons':
                case 'features':
                    // reserved words, not addon prefixes
                    break
                default:
                    if (nodeName == 'root') nodeName = ''
                    node.value.each { addon ->
                        String pluginName = GriffonNameUtils.getHyphenatedName(addon.key - 'GriffonAddon')
                        Map config = ADDON_CACHE[pluginName]
                        if (config) {
                            config.node = addon
                            config.prefix = nodeName
                        }
                    }
            }
        }
    }

    static void handleAddonsAtStartup(GriffonApplication app) {
        LOG.info("Loading addons [START]")
        app.event(GriffonApplication.Event.LOAD_ADDONS_START.name, [app])

        computeAddonCache(app)
        for (config in getAddonCache().values()) {
            handleAddon(app, config)
        }

        app.addonManager.addons.each {name, addon ->
            try {
                addon.addonPostInit(app)
            } catch (MissingMethodException mme) {
                if (mme.method != 'addonPostInit') throw mme
            }
            app.event(GriffonApplication.Event.LOAD_ADDON_END.name, [name, addon, app])
            if (LOG.infoEnabled) LOG.info("Loaded addon $name")
        }

        app.event(GriffonApplication.Event.LOAD_ADDONS_END.name, [app, app.addonManager.addons])
        LOG.info("Loading addons [END]")
    }

    private static void handleAddon(GriffonApplication app, Map config) {
        resolveAddonClass(config)
        if (!config.addonClass) return

        if (FactoryBuilderSupport.isAssignableFrom(config.addonClass)) return

        GriffonAddonDescriptor addonDescriptor = app.addonManager.findAddonDescriptor(config.name)
        if (addonDescriptor) return

        def obj = config.addonClass.newInstance()
        GriffonAddon addon = obj instanceof GriffonAddon ? obj : new DefaultGriffonAddon(app, obj)
        addonDescriptor = new DefaultGriffonAddonDescriptor(config.prefix, config.className, config.name, config.version, addon)

        app.addonManager.registerAddon(addonDescriptor)

        MetaClass addonMetaClass = obj.metaClass
        if (!(obj instanceof GriffonAddon)) {
            addonMetaClass.app = app
            addonMetaClass.newInstance = GriffonApplicationHelper.&newInstance.curry(app)
        }
        if (!(obj instanceof ThreadingHandler)) UIThreadManager.enhance(addonMetaClass)

        if (LOG.infoEnabled) LOG.info("Loading addon ${config.name} with class ${addon.class.name}")
        app.event(GriffonApplication.Event.LOAD_ADDON_START.name, [config.name, addon, app])

        addon.addonInit(app)
        addMVCGroups(app, getAddonPropertyAsMap(addon, 'mvcGroups'))
        addEvents(app, getAddonPropertyAsMap(addon, 'events'))
    }

    static void handleAddonsForBuilders(GriffonApplication app, UberBuilder builder, Map<String, MetaClass> targets) {
        computeAddonCache(app)
        for (config in getAddonCache().values()) {
            handleAddonForBuilder(app, builder, targets, config)
        }

        app.addonManager.addons.each {name, addon ->
            try {
                addon.addonBuilderPostInit(app, builder)
            } catch (MissingMethodException mme) {
                if (mme.method != 'addonBuilderPostInit') throw mme
            }
        }
    }

    private static void resolveAddonClass(Map config) {
        String className = config.className

        if (!className.contains(".")) {
            String fixedClassName = 'addon.' + className
            try {
                config.addonClass = ApplicationClassLoader.get().loadClass(fixedClassName)
                config.className = fixedClassName
            } catch (ClassNotFoundException cnfe) {
                try {
                    config.addonClass = ApplicationClassLoader.get().loadClass(className)
                } catch (ClassNotFoundException cnfe2) {
                    if (config.node) {
                        throw cnfe2
                    }
                }

            }
        } else {
            try {
                config.addonClass = ApplicationClassLoader.get().loadClass(className)
            } catch (ClassNotFoundException cnfe) {
                if (config.node) {
                    throw cnfe
                }
            }
        }
    }

    static void handleAddonForBuilder(GriffonApplication app, UberBuilder builder, Map<String, MetaClass> targets, Map addonConfig) {
        resolveAddonClass(addonConfig)
        if (!addonConfig.addonClass) return

        if (FactoryBuilderSupport.isAssignableFrom(addonConfig.addonClass)) return

        String addonName = addonConfig.name
        String prefix = addonConfig.prefix
        GriffonAddon addon = app.addonManager.findAddon(addonName)

        addon.addonBuilderInit(app, builder)

        DELEGATE_TYPES.each { String delegateType ->
            List<Closure> delegates = getAddonPropertyAsList(addon, delegateType)
            delegateType = delegateType[0].toUpperCase() + delegateType[1..-2]
            delegates.each { Closure delegateValue ->
                builder."add$delegateType"(delegateValue)
            }
        }

        Map factories = getAddonPropertyAsMap(addon, 'factories')
        addFactories(builder, factories, addonName, prefix)

        Map methods = getAddonPropertyAsMap(addon, 'methods')
        addMethods(builder, methods, addonName, prefix)

        Map props = getAddonPropertyAsMap(addon, 'props')
        addProperties(builder, props, addonName, prefix)

        for (partialTarget in addonConfig.node?.value) {
            if (partialTarget.key == 'view') {
                // this needs special handling, skip it for now
                continue
            }
            MetaClass mc = targets[partialTarget.key]
            if (!mc) continue
            def values = partialTarget.value
            if (values instanceof String) values = [partialTarget.value]
            for (String itemName in values) {
                if (itemName == '*') {
                    if (methods && LOG.traceEnabled) LOG.trace("Injecting all methods on $partialTarget.key")
                    _addMethods(mc, methods, prefix)
                    if (factories && LOG.traceEnabled) LOG.trace("Injecting all factories on $partialTarget.key")
                    _addFactories(mc, factories, prefix, builder)
                    if (props && LOG.traceEnabled) LOG.trace("Injecting all properties on $partialTarget.key")
                    _addProps(mc, props, prefix)
                    continue
                } else if (itemName == '*:methods') {
                    if (methods && LOG.traceEnabled) LOG.trace("Injecting all methods on $partialTarget.key")
                    _addMethods(mc, methods, prefix)
                    continue
                } else if (itemName == '*:factories') {
                    if (factories && LOG.traceEnabled) LOG.trace("Injecting all factories on $partialTarget.key")
                    _addFactories(mc, factories, prefix, builder)
                    continue
                } else if (itemName == '*:props') {
                    if (props && LOG.traceEnabled) LOG.trace("Injecting all properties on $partialTarget.key")
                    _addProps(mc, props, prefix)
                    continue
                }

                def resolvedName = prefix + itemName
                if (methods.containsKey(itemName)) {
                    if (LOG.traceEnabled) LOG.trace("Injected method ${resolvedName}() on $partialTarget.key")
                    mc."$resolvedName" = methods[itemName]
                } else if (props.containsKey(itemName)) {
                    Map accessors = props[itemName]
                    String beanName
                    if (itemName.length() > 1) {
                        beanName = itemName[0].toUpperCase() + itemName.substring(1)
                    } else {
                        beanName = itemName[0].toUpperCase()
                    }
                    if (accessors.containsKey('get')) {
                        if (LOG.traceEnabled) LOG.trace("Injected getter for ${beanName} on $partialTarget.key")
                        mc."get$beanName" = accessors['get']
                    }
                    if (accessors.containsKey('set')) {
                        if (LOG.traceEnabled) LOG.trace("Injected setter for ${beanName} on $partialTarget.key")
                        mc."set$beanName" = accessors['set']
                    }
                } else if (factories.containsKey(itemName)) {
                    if (LOG.traceEnabled) LOG.trace("Injected factory ${resolvedName} on $partialTarget.key")
                    mc."${resolvedName}" = {Object... args -> builder."$resolvedName"(* args)}
                }
            }
        }
    }

    private static void _addMethods(MetaClass mc, Map methods, String prefix) {
        methods.each { mk, mv -> mc."${prefix}${mk}" = mv }
    }

    private static void _addFactories(MetaClass mc, Map factories, String prefix, UberBuilder builder) {
        factories.each { fk, fv ->
            def resolvedName = prefix + fk
            mc."$resolvedName" = {Object... args -> builder."$resolvedName"(* args) }
        }
    }

    private static void _addProps(MetaClass mc, Map props, String prefix) {
        props.each { beanName, accessors ->
            if (accessors.containsKey('get')) mc."${getGetterName(beanName)}" = accessors['get']
            if (accessors.containsKey('set')) mc."s${getSetterName(beanName)}" = accessors['set']
        }
    }

    static void addMVCGroups(GriffonApplication app, Map<String, Map<String, Object>> groups) {
        Map<String, Map<String, Object>> mvcGroups = (Map<String, Map<String, Object>>) groups;
        for (Map.Entry<String, Map<String, Object>> groupEntry : mvcGroups.entrySet()) {
            String type = groupEntry.getKey();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding MVC group " + type);
            }
            Map<String, Object> members = groupEntry.getValue();
            Map<String, Object> configMap = new LinkedHashMap<String, Object>();
            Map<String, String> membersCopy = new LinkedHashMap<String, String>();
            for (Object o : members.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                String key = String.valueOf(entry.getKey());
                if ("config".equals(key) && entry.getValue() instanceof Map) {
                    configMap = (Map<String, Object>) entry.getValue();
                } else {
                    membersCopy.put(key, String.valueOf(entry.getValue()));
                }
            }
            MVCGroupConfiguration configuration = app.getMvcGroupManager().newMVCGroupConfiguration(type, membersCopy, configMap);
            app.getMvcGroupManager().addConfiguration(configuration);
        }
    }

    static void addFactories(UberBuilder builder, Map<String, Object> factories, String addonName, String prefix) {
        for (Map.Entry<String, Object> entry : factories.entrySet()) {
            CompositeBuilderHelper.addFactory(builder, addonName, prefix + entry.getKey(), entry.getValue());
        }
    }

    static void addMethods(UberBuilder builder, Map<String, Closure> methods, String addonName, String prefix) {
        for (Map.Entry<String, Closure> entry : methods.entrySet()) {
            CompositeBuilderHelper.addMethod(builder, addonName, prefix + entry.getKey(), entry.getValue());
        }
    }

    static void addProperties(UberBuilder builder, Map<String, Map<String, Closure>> props, String addonName, String prefix) {
        for (Map.Entry<String, Map<String, Closure>> entry : props.entrySet()) {
            CompositeBuilderHelper.addProperty(builder, addonName, prefix + entry.getKey(), entry.getValue().get("get"), entry.getValue().get("set"));
        }
    }

    static void addEvents(GriffonApplication app, Map<String, Closure> events) {
        for (Map.Entry<String, Closure> entry : events.entrySet()) {
            app.addApplicationEventListener(entry.getKey(), entry.getValue());
        }
    }

    private static Map getAddonPropertyAsMap(GriffonAddon addon, String propertyName) {
        Map property = [:]

        try {
            // property access
            property = addon[propertyName]
            if (property != null && !property.isEmpty()) return property
        } catch (Exception e) {
            // ignore
        }

        try {
            // invoke getter
            property = addon."${getGetterName(propertyName)}"()
            if (property != null && !property.isEmpty()) return property
        } catch (Exception e) {
            // ignore
        }

        try {
            // direct field access
            property = addon.@"$propertyName"
            if (property != null && !property.isEmpty()) return property
        } catch (Exception e) {
            // ignore
        }

        return [:]
    }

    private static List getAddonPropertyAsList(GriffonAddon addon, String propertyName) {
        List property = []

        try {
            // property access
            property = addon[propertyName]
            if (property != null && !property.isEmpty()) return property
        } catch (Exception e) {
            // ignore
        }

        try {
            // invoke getter
            property = addon."${getGetterName(propertyName)}"()
            if (property != null && !property.isEmpty()) return property
        } catch (Exception e) {
            // ignore
        }

        try {
            // direct field access
            property = addon.@"$propertyName"
            if (property != null && !property.isEmpty()) return property
        } catch (Exception e) {
            // ignore
        }

        return []
    }
}
