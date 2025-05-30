/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2018, 2021 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.loader;

import com.ibm.sharedclasses.spi.SharedClassProvider; 				//OpenJ9-shared_classes_misc

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.lang.module.ModuleReader;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;							//OpenJ9-shared_classes_misc
import java.nio.file.Path;							//OpenJ9-shared_classes_misc
import java.nio.file.Paths;							//OpenJ9-shared_classes_misc
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;							//OpenJ9-shared_classes_misc
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntConsumer;						//OpenJ9-shared_classes_misc
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.module.ModulePatcher.PatchedModuleReader;
import jdk.internal.module.Resources;
import jdk.internal.vm.annotation.Stable;


/**
 * The platform or application class loader. Resources loaded from modules
 * defined to the boot class loader are also loaded via an instance of this
 * ClassLoader type.
 *
 * <p> This ClassLoader supports loading of classes and resources from modules.
 * Modules are defined to the ClassLoader by invoking the {@link #loadModule}
 * method. Defining a module to this ClassLoader has the effect of making the
 * types in the module visible. </p>
 *
 * <p> This ClassLoader also supports loading of classes and resources from a
 * class path of URLs that are specified to the ClassLoader at construction
 * time. The class path may expand at runtime (the Class-Path attribute in JAR
 * files or via instrumentation agents). </p>
 *
 * <p> The delegation model used by this ClassLoader differs to the regular
 * delegation model. When requested to load a class then this ClassLoader first
 * maps the class name to its package name. If there is a module defined to a
 * BuiltinClassLoader containing this package then the class loader delegates
 * directly to that class loader. If there isn't a module containing the
 * package then it delegates the search to the parent class loader and if not
 * found in the parent then it searches the class path. The main difference
 * between this and the usual delegation model is that it allows the platform
 * class loader to delegate to the application class loader, important with
 * upgraded modules defined to the platform class loader.
 */

public class BuiltinClassLoader
    extends SecureClassLoader
{
    static {
        if (!ClassLoader.registerAsParallelCapable())
            throw new InternalError("Unable to register as parallel capable");
    }

    // parent ClassLoader
    private final BuiltinClassLoader parent;

    // the URL class path, or null if there is no class path
    private @Stable URLClassPath ucp;
    // Private member fields used for Shared classes                          		//OpenJ9-shared_classes_misc
    private static URL jimageURL;													//OpenJ9-shared_classes_misc
    private SharedClassProvider sharedClassServiceProvider ;						//OpenJ9-shared_classes_misc
    private SharedClassMetaDataCache sharedClassMetaDataCache;                 		//OpenJ9-shared_classes_misc
    																				//OpenJ9-shared_classes_misc
    /*                                                                           	//OpenJ9-shared_classes_misc
     * Wrapper class for maintaining the index of where the metadata (codesource and manifest)  //OpenJ9-shared_classes_misc
     * is found - used only in Shared classes context.                           	//OpenJ9-shared_classes_misc
     */                                                                          	//OpenJ9-shared_classes_misc
    private static class SharedClassIndexHolder {  								 	//OpenJ9-shared_classes_misc
        int index;                                                              	//OpenJ9-shared_classes_misc
    																				//OpenJ9-shared_classes_misc
        public void setIndex(int index) {                                        	//OpenJ9-shared_classes_misc
            this.index = index;                                                  	//OpenJ9-shared_classes_misc
        }                                                                        	//OpenJ9-shared_classes_misc
    }                                                                            	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
    /*                                                                           	//OpenJ9-shared_classes_misc
     * Wrapper class for internal storage of metadata (codesource and manifest) associated with   //OpenJ9-shared_classes_misc
     * shared class - used only in Shared classes context.                       	//OpenJ9-shared_classes_misc
     */                                                                          	//OpenJ9-shared_classes_misc
    private class SharedClassMetaData {                                          	//OpenJ9-shared_classes_misc
        private CodeSource codeSource;                                           	//OpenJ9-shared_classes_misc
        private Manifest manifest;                                               	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
        SharedClassMetaData(CodeSource codeSource, Manifest manifest) {          	//OpenJ9-shared_classes_misc
            this.codeSource = codeSource;                                        	//OpenJ9-shared_classes_misc
            this.manifest = manifest;                                            	//OpenJ9-shared_classes_misc
        }                                                                        	//OpenJ9-shared_classes_misc
        public CodeSource getCodeSource() { return codeSource; }                 	//OpenJ9-shared_classes_misc
        public Manifest getManifest() { return manifest; }                       	//OpenJ9-shared_classes_misc
    }                                                                            	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
    /*                                                                           	//OpenJ9-shared_classes_misc
     * Represents a collection of SharedClassMetaData objects retrievable by     	//OpenJ9-shared_classes_misc
     * index.                                                                    	//OpenJ9-shared_classes_misc
     */                                                                          	//OpenJ9-shared_classes_misc
    private class SharedClassMetaDataCache {                                   	//OpenJ9-shared_classes_misc
        private final static int BLOCKSIZE = 10;                                 	//OpenJ9-shared_classes_misc
        private SharedClassMetaData[] store;                                     	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
        public SharedClassMetaDataCache(int initialSize) {                       	//OpenJ9-shared_classes_misc
            // Allocate space for an initial amount of metadata entries				//OpenJ9-shared_classes_misc
            store = new SharedClassMetaData[initialSize];                        	//OpenJ9-shared_classes_misc
        }                                                                        	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
        /**                                                                      	//OpenJ9-shared_classes_misc
         * Retrieves the SharedClassMetaData stored at the given index, or null  	//OpenJ9-shared_classes_misc
         * if no SharedClassMetaData was previously stored at the given index    	//OpenJ9-shared_classes_misc
         * or the index is out of range.                                         	//OpenJ9-shared_classes_misc
         */                                                                      	//OpenJ9-shared_classes_misc
        public synchronized SharedClassMetaData getSharedClassMetaData(int index) { //OpenJ9-shared_classes_misc
            if (index < 0 || store.length < (index+1)) {                         	//OpenJ9-shared_classes_misc
                return null;                                                     	//OpenJ9-shared_classes_misc
            }                                                                    	//OpenJ9-shared_classes_misc
            return store[index];                                                 	//OpenJ9-shared_classes_misc
        }                                                                        	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
        /**                                                                      	//OpenJ9-shared_classes_misc
         * Stores the supplied SharedClassMetaData at the given index in the     	//OpenJ9-shared_classes_misc
         * store. The store will be grown to contain the index if necessary.     	//OpenJ9-shared_classes_misc
         */                                                                      	//OpenJ9-shared_classes_misc
        public synchronized void setSharedClassMetaData(int index,               	//OpenJ9-shared_classes_misc
                                                     SharedClassMetaData data) {  	//OpenJ9-shared_classes_misc
            ensureSize(index);                                                   	//OpenJ9-shared_classes_misc
            store[index] = data;                                                 	//OpenJ9-shared_classes_misc
        }                                                                        	//OpenJ9-shared_classes_misc
																					//OpenJ9-shared_classes_misc
        // Ensure that the store can hold at least index number of entries    		//OpenJ9-shared_classes_misc
        private synchronized void ensureSize(int index) {                        	//OpenJ9-shared_classes_misc
            if (store.length < (index+1)) {                                      	//OpenJ9-shared_classes_misc
                int newSize = (index+BLOCKSIZE);                                 	//OpenJ9-shared_classes_misc
                SharedClassMetaData[] newSCMDS = new SharedClassMetaData[newSize];  //OpenJ9-shared_classes_misc
                System.arraycopy(store, 0, newSCMDS, 0, store.length);           	//OpenJ9-shared_classes_misc
                store = newSCMDS;                                                	//OpenJ9-shared_classes_misc
            }                                                                    	//OpenJ9-shared_classes_misc
        }                                                                        	//OpenJ9-shared_classes_misc
    }

	/*                                                                           	//OpenJ9-shared_classes_misc
	 * Initialize support for shared classes.                                    	//OpenJ9-shared_classes_misc
	 */																				//OpenJ9-shared_classes_misc
	public synchronized void initializeSharedClassesSupport() {						//OpenJ9-shared_classes_misc
		int ucpLength = 0;															//OpenJ9-shared_classes_misc
		if (isSharedClassesEnabled() 													//OpenJ9-shared_classes_miscs
			&& (null == sharedClassServiceProvider)										//OpenJ9-shared_classes_misc
		) {																				//OpenJ9-shared_classes_misc
			URL[] initialClassPath = null;												//OpenJ9-shared_classes_misc
			if (ucp != null) {															//OpenJ9-shared_classes_misc
				initialClassPath = ucp.getURLs();										//OpenJ9-shared_classes_misc
				ucpLength = initialClassPath.length;									//OpenJ9-shared_classes_misc
			}																			//OpenJ9-shared_classes_misc
			ServiceLoader<SharedClassProvider> sl = ServiceLoader.load(SharedClassProvider.class);	//OpenJ9-shared_classes_misc
			for (SharedClassProvider p : sl) {														//OpenJ9-shared_classes_misc
				if (null != p) {																	//OpenJ9-shared_classes_misc
					if (null != p.initializeProvider(this, initialClassPath, true, true)){			//OpenJ9-shared_classes_misc
						sharedClassServiceProvider = p;												//OpenJ9-shared_classes_misc
						if (null != ucp) {															//OpenJ9-shared_classes_misc
							ucp.setSharedClassProvider(sharedClassServiceProvider);					//OpenJ9-shared_classes_misc
						}																			//OpenJ9-shared_classes_misc
						break;																		//OpenJ9-shared_classes_misc
					}																				//OpenJ9-shared_classes_misc
				}																					//OpenJ9-shared_classes_misc
			}																						//OpenJ9-shared_classes_misc
		}																							//OpenJ9-shared_classes_misc

		if (usingSharedClasses() && null == sharedClassMetaDataCache) {            					//OpenJ9-shared_classes_misc
			// Create a metadata cache                                   							//OpenJ9-shared_classes_misc
			sharedClassMetaDataCache = new SharedClassMetaDataCache(ucpLength);  					//OpenJ9-shared_classes_misc
		}                                                                    						//OpenJ9-shared_classes_misc
	}                                																//OpenJ9-shared_classes_misc
    /*                                                                          					//OpenJ9-shared_classes_misc
     * Return true if shared classes support is active, otherwise false.         					//OpenJ9-shared_classes_misc
     */                                                                          					//OpenJ9-shared_classes_misc
    private boolean usingSharedClasses() {                                       					//OpenJ9-shared_classes_misc
        return (sharedClassServiceProvider != null);         										//OpenJ9-shared_classes_misc
    }

    /*                                                                          				//OpenJ9-shared_classes_misc
     * Returns if -Xshareclasses is used in the command line         							//OpenJ9-shared_classes_misc
     */  																						//OpenJ9-shared_classes_misc
    private static boolean isSharedClassesEnabled() {											//OpenJ9-shared_classes_misc
    	/* 																						//OpenJ9-shared_classes_misc
    	 * Lambda expression can't be used here due to bootstrap problem 						//OpenJ9-shared_classes_misc
    	 * when jdk.internal.lambda.dumpProxyClasses is enabled.								//OpenJ9-shared_classes_misc
    	 * More details are at https://github.com/eclipse-openj9/openj9/issues/3399					//OpenJ9-shared_classes_misc
    	 */																						//OpenJ9-shared_classes_misc
    	return Boolean.getBoolean("com.ibm.oti.shared.enabled");						//OpenJ9-shared_classes_misc
   	}																							//OpenJ9-shared_classes_misc

    /*                                                                          					//OpenJ9-shared_classes_misc
     * Gets the URL of the jimage file         														//OpenJ9-shared_classes_misc
     */  																							//OpenJ9-shared_classes_misc
	private static void setJimageURL() {															//OpenJ9-shared_classes_misc
    	/* 																						//OpenJ9-shared_classes_misc
    	 * Lambda expression can't be used here due to bootstrap problem 						//OpenJ9-shared_classes_misc
    	 * when jdk.internal.lambda.dumpProxyClasses is enabled.								//OpenJ9-shared_classes_misc
    	 * More details are at https://github.com/eclipse-openj9/openj9/issues/3399					//OpenJ9-shared_classes_misc
    	 */																						//OpenJ9-shared_classes_misc
		String javaHome = System.getProperty("java.home");										//OpenJ9-shared_classes_misc
		Path p = Paths.get(javaHome, "lib", "modules");											//OpenJ9-shared_classes_misc
		if (Files.isRegularFile(p)) {															//OpenJ9-shared_classes_misc
			try {																				//OpenJ9-shared_classes_misc
				jimageURL = p.toUri().toURL();													//OpenJ9-shared_classes_misc
			} catch (MalformedURLException e) {}												//OpenJ9-shared_classes_misc
		}																						//OpenJ9-shared_classes_misc
	}																							//OpenJ9-shared_classes_misc

    /**
     * A module defined/loaded by a built-in class loader.
     *
     * A LoadedModule encapsulates a ModuleReference along with its CodeSource
     * URL to avoid needing to create this URL when defining classes.
     */
    private static class LoadedModule {
        private final BuiltinClassLoader loader;
        private final ModuleReference mref;
        private final URI uri;                      // may be null
        private @Stable URL codeSourceURL;          // may be null
		private final URL fileURL;																//OpenJ9-shared_classes_misc

        LoadedModule(BuiltinClassLoader loader, ModuleReference mref) {
            URL url = null;
            this.uri = mref.location().orElse(null);

            // for non-jrt schemes we need to resolve the codeSourceURL
            // eagerly during bootstrap since the handler might be
            // overridden
            if (uri != null && !"jrt".equals(uri.getScheme())) {
                url = createURL(uri);
            }
            this.loader = loader;
            this.mref = mref;
            this.codeSourceURL = url;
			this.fileURL = convertJrtToFileURL();												//OpenJ9-shared_classes_misc
        }

        BuiltinClassLoader loader() { return loader; }
        ModuleReference mref() { return mref; }
        String name() { return mref.descriptor().name(); }

        URL codeSourceURL() {
            URL url = codeSourceURL;
            if (url == null && uri != null) {
                codeSourceURL = url = createURL(uri);
            }
            return url;
        }

        private URL createURL(URI uri) {
            URL url = null;
            try {
                url = uri.toURL();
            } catch (MalformedURLException | IllegalArgumentException e) {
            }
            return url;
        }
		URL fileURL() {return fileURL;}
		URL convertJrtToFileURL() {																//OpenJ9-shared_classes_misc
			if (null == jimageURL) {															//OpenJ9-shared_classes_misc
			   setJimageURL();																	//OpenJ9-shared_classes_misc
			}																					//OpenJ9-shared_classes_misc
			if (null != jimageURL) {															//OpenJ9-shared_classes_misc
				if (codeSourceURL().getProtocol().equals("jrt")) {								//OpenJ9-shared_classes_misc
					return jimageURL;															//OpenJ9-shared_classes_misc
				}																				//OpenJ9-shared_classes_misc
			}																					//OpenJ9-shared_classes_misc
			return this.codeSourceURL();															//OpenJ9-shared_classes_misc
		}																						//OpenJ9-shared_classes_misc
    }

    // maps package name to loaded module for modules in the boot layer
    private static final Map<String, LoadedModule> packageToModule;
    static {
        ArchivedClassLoaders archivedClassLoaders = ArchivedClassLoaders.get();
        if (archivedClassLoaders != null) {
            @SuppressWarnings("unchecked")
            Map<String, LoadedModule> map
                = (Map<String, LoadedModule>) archivedClassLoaders.packageToModule();
            packageToModule = map;
        } else {
            packageToModule = new ConcurrentHashMap<>(1024);
        }
    }

    /**
     * Invoked by ArchivedClassLoaders to archive the package-to-module map.
     */
    static Map<String, ?> packageToModule() {
        return packageToModule;
    }

    // maps a module name to a module reference
    private final Map<String, ModuleReference> nameToModule;

    // maps a module reference to a module reader
    private final Map<ModuleReference, ModuleReader> moduleToReader;

    // cache of resource name -> list of URLs.
    // used only for resources that are not in module packages
    private volatile SoftReference<Map<String, List<URL>>> resourceCache;

    /**
     * Create a new instance.
     */
    BuiltinClassLoader(String name, BuiltinClassLoader parent, URLClassPath ucp) {
        // ensure getParent() returns null when the parent is the boot loader
        super(name, parent == null || parent == ClassLoaders.bootLoader() ? null : parent);

        this.parent = parent;
        this.ucp = ucp;

        this.nameToModule = new ConcurrentHashMap<>(32);
        this.moduleToReader = new ConcurrentHashMap<>();
    }

    /**
     * Appends to the given file path to the class path.
     */
    void appendClassPath(String path) {
        // assert ucp != null;
        ucp.addFile(path);
    }

    /**
     * Sets the class path, called to reset the class path during -Xshare:dump
     */
    void setClassPath(URLClassPath ucp) {
        this.ucp = ucp;
    }

    /**
     * Returns {@code true} if there is a class path associated with this
     * class loader.
     */
    boolean hasClassPath() {
        return ucp != null;
    }

    /**
     * Register a module this class loader. This has the effect of making the
     * types in the module visible.
     */
    public void loadModule(ModuleReference mref) {
        ModuleDescriptor descriptor = mref.descriptor();
        String mn = descriptor.name();
        if (nameToModule.putIfAbsent(mn, mref) != null) {
            throw new InternalError(mn + " already defined to this loader");
        }

        LoadedModule loadedModule = new LoadedModule(this, mref);
        for (String pn : descriptor.packages()) {
            LoadedModule other = packageToModule.putIfAbsent(pn, loadedModule);
            if (other != null) {
                throw new InternalError(pn + " in modules " + mn + " and "
                                        + other.name());
            }
        }

        // clear resources cache if VM is already initialized
        if (resourceCache != null && VM.isModuleSystemInited()) {
            resourceCache = null;
        }
    }

    /**
     * Returns the {@code ModuleReference} for the named module defined to
     * this class loader; or {@code null} if not defined.
     *
     * @param name The name of the module to find
     */
    protected ModuleReference findModule(String name) {
        return nameToModule.get(name);
    }


    // -- finding resources

    /**
     * Returns a URL to a resource of the given name in a module defined to
     * this class loader.
     */
    @Override
    public URL findResource(String mn, String name) throws IOException {
        URL url = null;

        if (mn != null) {
            // find in module
            ModuleReference mref = nameToModule.get(mn);
            if (mref != null) {
                url = findResource(mref, name);
            }
        } else {
            // find on class path
            url = findResourceOnClassPath(name);
        }

        return url;
    }

    /**
     * Returns an input stream to a resource of the given name in a module
     * defined to this class loader.
     */
    public InputStream findResourceAsStream(String mn, String name)
        throws IOException
    {
        InputStream in = null;
        if (mn != null) {
            // find in module defined to this loader
            ModuleReference mref = nameToModule.get(mn);
            if (mref != null) {
                in = moduleReaderFor(mref).open(name).orElse(null);
            }
        } else {
            URL url = findResourceOnClassPath(name);
            if (url != null) {
                in = url.openStream();
            }
        }
        return in;
    }

    /**
     * Finds a resource with the given name in the modules defined to this
     * class loader or its class path.
     */
    @Override
    public URL findResource(String name) {
        String pn = Resources.toPackageName(name);
        LoadedModule module = packageToModule.get(pn);
        if (module != null) {

            // resource is in a package of a module defined to this loader
            if (module.loader() == this) {
                URL url;
                try {
                    url = findResource(module.name(), name);
                } catch (IOException ioe) {
                    return null;
                }
                if (url != null
                    && (name.endsWith(".class")
                        || url.toString().endsWith("/")
                        || isOpen(module.mref(), pn))) {
                    return url;
                }
            }

        } else {

            // not in a module package but may be in module defined to this loader
            try {
                List<URL> urls = findMiscResource(name);
                if (!urls.isEmpty()) {
                    URL url = urls.get(0);
                    if (url != null) {
                        return url;
                    }
                }
            } catch (IOException ioe) {
                return null;
            }

        }

        // search class path
        return findResourceOnClassPath(name);
    }

    /**
     * Returns an enumeration of URL objects to all the resources with the
     * given name in modules defined to this class loader or on the class
     * path of this loader.
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        List<URL> resources = new ArrayList<>();  // list of resource URLs

        String pn = Resources.toPackageName(name);
        LoadedModule module = packageToModule.get(pn);
        if (module != null) {

            // resource is in a package of a module defined to this loader
            if (module.loader() == this) {
                URL url = findResource(module.name(), name);
                if (url != null
                    && (name.endsWith(".class")
                        || url.toString().endsWith("/")
                        || isOpen(module.mref(), pn))) {
                    resources.add(url);
                }
            }

        } else {
            // not in a package of a module defined to this loader
            for (URL url : findMiscResource(name)) {
                if (url != null) {
                    resources.add(url);
                }
            }
        }

        // class path
        Enumeration<URL> e = findResourcesOnClassPath(name);

        // concat the URLs of the resource in the modules and the class path
        return new Enumeration<>() {
            final Iterator<URL> iterator = resources.iterator();
            URL next;
            private boolean hasNext() {
                if (next != null) {
                    return true;
                } else if (iterator.hasNext()) {
                    next = iterator.next();
                    return true;
                } else {
                    while (e.hasMoreElements() && next == null) {
                        next = e.nextElement();
                    }
                    return next != null;
                }
            }
            @Override
            public boolean hasMoreElements() {
                return hasNext();
            }
            @Override
            public URL nextElement() {
                if (hasNext()) {
                    URL result = next;
                    next = null;
                    return result;
                } else {
                    throw new NoSuchElementException();
                }
            }
        };

    }

    /**
     * Returns the list of URLs to a "miscellaneous" resource in modules
     * defined to this loader. A miscellaneous resource is not in a module
     * package, e.g. META-INF/services/p.S.
     *
     * The cache used by this method avoids repeated searching of all modules.
     */
    private List<URL> findMiscResource(String name) throws IOException {
        SoftReference<Map<String, List<URL>>> ref = this.resourceCache;
        Map<String, List<URL>> map = (ref != null) ? ref.get() : null;
        if (map == null) {
            // only cache resources after VM is fully initialized
            if (VM.isModuleSystemInited()) {
                map = new ConcurrentHashMap<>();
                this.resourceCache = new SoftReference<>(map);
            }
        } else {
            List<URL> urls = map.get(name);
            if (urls != null)
                return urls;
        }

        // search all modules for the resource
        List<URL> urls = null;
        for (ModuleReference mref : nameToModule.values()) {
            URI u = moduleReaderFor(mref).find(name).orElse(null);
            if (u != null) {
                try {
                    if (urls == null)
                        urls = new ArrayList<>();
                    urls.add(u.toURL());
                } catch (MalformedURLException | IllegalArgumentException e) {
                }
            }
        }
        if (urls == null) {
            urls = List.of();
        }

        // only cache resources after VM is fully initialized
        if (map != null) {
            map.putIfAbsent(name, urls);
        }

        return urls;
    }

    /**
     * Returns the URL to a resource in a module or {@code null} if not found.
     */
    private URL findResource(ModuleReference mref, String name) throws IOException {
        URI u = moduleReaderFor(mref).find(name).orElse(null);
        if (u != null) {
            try {
                return u.toURL();
            } catch (MalformedURLException | IllegalArgumentException e) { }
        }
        return null;
    }

    /**
     * Returns a URL to a resource on the class path.
     */
    private URL findResourceOnClassPath(String name) {
        if (hasClassPath()) {
            return ucp.findResource(name);
        } else {
            // no class path
            return null;
        }
    }

    /**
     * Returns the URLs of all resources of the given name on the class path.
     */
    private Enumeration<URL> findResourcesOnClassPath(String name) {
        if (hasClassPath()) {
            return ucp.findResources(name);
        } else {
            // no class path
            return Collections.emptyEnumeration();
        }
    }

    // -- finding/loading classes

    /**
     * Finds the class with the specified binary name.
     */
    @Override
    protected Class<?> findClass(String cn) throws ClassNotFoundException {
        // no class loading until VM is fully initialized
        if (!VM.isModuleSystemInited())
            throw new ClassNotFoundException(cn);

        // find the candidate module for this class
        LoadedModule loadedModule = findLoadedModule(cn);

        Class<?> c = null;
        if (loadedModule != null) {

            // attempt to load class in module defined to this loader
            if (loadedModule.loader() == this) {
                c = findClassInModuleOrNull(loadedModule, cn);
            }

        } else {

            // search class path
            if (hasClassPath()) {
                c = findClassOnClassPathOrNull(cn);
            }

        }

        // not found
        if (c == null)
            throw new ClassNotFoundException(cn);

        return c;
    }

    /**
     * Finds the class with the specified binary name in a module.
     * This method returns {@code null} if the class cannot be found
     * or not defined in the specified module.
     */
    @Override
    protected Class<?> findClass(String mn, String cn) {
        if (mn != null) {
            // find the candidate module for this class
            LoadedModule loadedModule = findLoadedModule(mn, cn);
            if (loadedModule == null) {
                return null;
            }

            // attempt to load class in module defined to this loader
            assert loadedModule.loader() == this;
            return findClassInModuleOrNull(loadedModule, cn);
        }

        // search class path
        if (hasClassPath()) {
            return findClassOnClassPathOrNull(cn);
        }

        return null;
    }

    /**
     * Loads the class with the specified binary name.
     */
    @Override
    protected Class<?> loadClass(String cn, boolean resolve)
        throws ClassNotFoundException
    {
        Class<?> c = loadClassOrNull(cn, resolve);
        if (c == null)
            throw new ClassNotFoundException(cn);
        return c;
    }

    /**
     * A variation of {@code loadClass} to load a class with the specified
     * binary name. This method returns {@code null} when the class is not
     * found.
     */
    protected Class<?> loadClassOrNull(String cn, boolean resolve) {
        synchronized (getClassLoadingLock(cn)) {
            // check if already loaded
            Class<?> c = findLoadedClass(cn);

            if (c == null) {

                // find the candidate module for this class
                LoadedModule loadedModule = findLoadedModule(cn);
                if (loadedModule != null) {

                    // package is in a module
                    BuiltinClassLoader loader = loadedModule.loader();
                    if (loader == this) {
                        if (VM.isModuleSystemInited()) {
								c = findClassInModuleOrNull(loadedModule, cn);
                        }
                    } else {
                        // delegate to the other loader
                        c = loader.loadClassOrNull(cn);
                    }

                } else {

                    // check parent
                    if (parent != null) {
                        c = parent.loadClassOrNull(cn);
                    }

                    // check class path
                    if (c == null && hasClassPath() && VM.isModuleSystemInited()) {
                        c = findClassOnClassPathOrNull(cn);
                    }
                }

            }

            if (resolve && c != null)
                resolveClass(c);

            return c;
        }
    }

    /**
     * A variation of {@code loadClass} to load a class with the specified
     * binary name. This method returns {@code null} when the class is not
     * found.
     */
    protected final Class<?> loadClassOrNull(String cn) {
        return loadClassOrNull(cn, false);
    }

    /**
     * Finds the candidate loaded module for the given class name.
     * Returns {@code null} if none of the modules defined to this
     * class loader contain the API package for the class.
     */
    private LoadedModule findLoadedModule(String cn) {
        int pos = cn.lastIndexOf('.');
        if (pos < 0)
            return null; // unnamed package

        String pn = cn.substring(0, pos);
        return packageToModule.get(pn);
    }

    /**
     * Finds the candidate loaded module for the given class name
     * in the named module.  Returns {@code null} if the named module
     * is not defined to this class loader or does not contain
     * the API package for the class.
     */
    private LoadedModule findLoadedModule(String mn, String cn) {
        LoadedModule loadedModule = findLoadedModule(cn);
        if (loadedModule != null && mn.equals(loadedModule.name())) {
            return loadedModule;
        } else {
            return null;
        }
    }

    /**
     * Finds the class with the specified binary name if in a module
     * defined to this ClassLoader.
     *
     * @return the resulting Class or {@code null} if not found
     */
    private Class<?> findClassInModuleOrNull(LoadedModule loadedModule, String cn) {
		Class<?> c = null;												//OpenJ9-shared_classes_misc
		ModuleReader reader = moduleReaderFor(loadedModule.mref()); //OpenJ9-shared_classes_misc
		if (!(reader instanceof PatchedModuleReader)) {					//OpenJ9-shared_classes_misc
			c = findClassInSharedClassesCache(cn, loadedModule, false);	//OpenJ9-shared_classes_misc
		}																//OpenJ9-shared_classes_misc
		if (null != c) {												//OpenJ9-shared_classes_misc
			return c;													//OpenJ9-shared_classes_misc
		}																//OpenJ9-shared_classes_misc

        return defineClass(cn, loadedModule);
    }

    /**
     * Finds the class with the specified binary name on the class path.
     *
     * @return the resulting Class or {@code null} if not found
     */
    private Class<?> findClassOnClassPathOrNull(String cn) {
		Class<?> c = findClassInSharedClassesCache(cn, null, true);		//OpenJ9-shared_classes_misc
		if (null != c) {												//OpenJ9-shared_classes_misc
			return c;													//OpenJ9-shared_classes_misc
		}																//OpenJ9-shared_classes_misc

        String path = cn.replace('.', '/').concat(".class");
        Resource res = ucp.getResource(path);
        if (res != null) {
            try {
                return defineClass(cn, res);
            } catch (IOException ioe) {
                // TBD on how I/O errors should be propagated
            }
        }
        return null;
    }

	 /**																											//OpenJ9-shared_classes_misc
     * Finds the class with the specified binary name in the shared classes cache.									//OpenJ9-shared_classes_misc
     *																												//OpenJ9-shared_classes_misc
     * @return the resulting Class or {@code null} if not found														//OpenJ9-shared_classes_misc
     */																												//OpenJ9-shared_classes_misc
	private Class<?> findClassInSharedClassesCache(String cn, LoadedModule module, boolean pkgCheck) {				//OpenJ9-shared_classes_misc
		if (usingSharedClasses()) {																					//OpenJ9-shared_classes_misc
			byte[] sharedClazz = null;																				//OpenJ9-shared_classes_misc
			CodeSource cs = null;																					//OpenJ9-shared_classes_misc
			Manifest man = null;																					//OpenJ9-shared_classes_misc
			boolean defineClass = false;																			//OpenJ9-shared_classes_misc
			if (null == module) {																					//OpenJ9-shared_classes_misc
				SharedClassIndexHolder sharedClassIndexHolder = new SharedClassIndexHolder(); 						//OpenJ9-shared_classes_misc
				IntConsumer consumer = (i)->sharedClassIndexHolder.setIndex(i); 									//OpenJ9-shared_classes_misc
				sharedClazz = sharedClassServiceProvider.findSharedClassURLClasspath(cn, consumer); 				//OpenJ9-shared_classes_misc
				if (sharedClazz != null) {                                      									//OpenJ9-shared_classes_misc
					int indexFoundData = sharedClassIndexHolder.index;          									//OpenJ9-shared_classes_misc
					SharedClassMetaData metadata = sharedClassMetaDataCache.getSharedClassMetaData(indexFoundData); //OpenJ9-shared_classes_misc
					if (metadata != null) {   																		//OpenJ9-shared_classes_misc
						cs = metadata.getCodeSource();																//OpenJ9-shared_classes_misc
						man = metadata.getManifest();																//OpenJ9-shared_classes_misc
						defineClass = true;																			//OpenJ9-shared_classes_misc
					} 																								//OpenJ9-shared_classes_misc
				}																									//OpenJ9-shared_classes_misc
			} else {																								//OpenJ9-shared_classes_misc
				// module has its own URL, find the class using the module URL										//OpenJ9-shared_classes_misc
				sharedClazz = sharedClassServiceProvider.findSharedClassURL(module.fileURL(), cn);					//OpenJ9-shared_classes_misc
				if (sharedClazz != null) { 																			//OpenJ9-shared_classes_misc
					cs = new CodeSource(module.codeSourceURL(), (CodeSigner[]) null);								//OpenJ9-shared_classes_misc
					defineClass = true;																				//OpenJ9-shared_classes_misc
				}																									//OpenJ9-shared_classes_misc
			}																									//OpenJ9-shared_classes_misc
			if (defineClass) {																					//OpenJ9-shared_classes_misc
				try {                                                   										//OpenJ9-shared_classes_misc
					Class<?> clazz = defineClass(cn, sharedClazz,         										//OpenJ9-shared_classes_misc
									cs,        																	//OpenJ9-shared_classes_misc
									man, pkgCheck);         													//OpenJ9-shared_classes_misc
					return clazz;                                       										//OpenJ9-shared_classes_misc
				} catch (IOException e) {                               										//OpenJ9-shared_classes_misc
					// defineClass() failed                                										//OpenJ9-shared_classes_misc
				}                                                      											//OpenJ9-shared_classes_misc
			}																									//OpenJ9-shared_classes_misc
		}																											//OpenJ9-shared_classes_misc
		return null;																								//OpenJ9-shared_classes_misc
	}


	/*                                                                          				//OpenJ9-shared_classes_misc
     * Defines a class using the class bytes, codesource and manifest           				//OpenJ9-shared_classes_misc
     * obtained from the specified shared class cache. The resulting            				//OpenJ9-shared_classes_misc
     * class must be resolved before it can be used.  This method is            				//OpenJ9-shared_classes_misc
     * used only in a Shared classes context.                                   				//OpenJ9-shared_classes_misc
     */                                                                        			 		//OpenJ9-shared_classes_misc
    private Class<?> defineClass(String name, byte[] sharedClazz, CodeSource codesource, Manifest man, boolean pkgCheck) throws IOException { //OpenJ9-shared_classes_misc
		if (pkgCheck) {																 			//OpenJ9-shared_classes_misc
			int i = name.lastIndexOf('.');                                          			//OpenJ9-shared_classes_misc
			if (-1 != i) {															 			//OpenJ9-shared_classes_misc
				String pkgname = name.substring(0, i);								 			//OpenJ9-shared_classes_misc
				URL url = codesource.getLocation();									 			//OpenJ9-shared_classes_misc
				defineOrCheckPackage(pkgname, man, url);							 			//OpenJ9-shared_classes_misc
			}																		 			//OpenJ9-shared_classes_misc
		}																			  			//OpenJ9-shared_classes_misc
       /*                                                                      					//OpenJ9-shared_classes_misc
         * Now read the class bytes and define the class.  We don't need to call  				//OpenJ9-shared_classes_misc
         * storeSharedClass(), since its already in our shared class cache.     				//OpenJ9-shared_classes_misc
         */                                                                     				//OpenJ9-shared_classes_misc
        return defineClass(name, sharedClazz, 0, sharedClazz.length, codesource); 				//OpenJ9-shared_classes_misc
     }                                                                          				//OpenJ9-shared_classes_misc

    /**
     * Defines the given binary class name to the VM, loading the class
     * bytes from the given module.
     *
     * @return the resulting Class or {@code null} if an I/O error occurs
     */
    private Class<?> defineClass(String cn, LoadedModule loadedModule) {
        ModuleReference mref = loadedModule.mref();
        ModuleReader reader = moduleReaderFor(mref);

        try {
            ByteBuffer bb = null;
            URL csURL = null;
            Manifest man = null;															//OpenJ9-shared_classes_misc
			boolean patched = false;

            // locate class file, special handling for patched modules to
            // avoid locating the resource twice
            String rn = cn.replace('.', '/').concat(".class");
            if (reader instanceof PatchedModuleReader) {
                Resource r = ((PatchedModuleReader)reader).findResource(rn);
                if (r != null) {
                    bb = r.getByteBuffer();
                    csURL = r.getCodeSourceURL();
					if (!loadedModule.codeSourceURL().equals(csURL)) {						//OpenJ9-shared_classes_misc
						// class is not from the module, it is from the patched path. Do not share in this case		//OpenJ9-shared_classes_misc
						patched = true;														//OpenJ9-shared_classes_misc
					}																		//OpenJ9-shared_classes_misc
                }
            } else {
                bb = reader.read(rn).orElse(null);
                csURL = loadedModule.codeSourceURL();
            }

            if (bb == null) {
                // class not found
                return null;
            }

            CodeSource cs = new CodeSource(csURL, (CodeSigner[]) null);
            try {
                // define class to VM
                Class<?> clazz = defineClass(cn, bb, cs);
				if ((null != clazz) && !patched) {											//OpenJ9-shared_classes_misc
					//ignore the return value of storeClassIntoSharedClassesCache()			//OpenJ9-shared_classes_misc
					storeClassIntoSharedClassesCache(clazz, cs, man, -1, loadedModule);		//OpenJ9-shared_classes_misc
				}																			//OpenJ9-shared_classes_misc
				return clazz;
            } finally {
                reader.release(bb);
            }

        } catch (IOException ioe) {
            // TBD on how I/O errors should be propagated
            return null;
        }
    }

	/**																											//OpenJ9-shared_classes_misc
     * Stores the class into the shared classes cache.															//OpenJ9-shared_classes_misc
     *																											//OpenJ9-shared_classes_misc
     * @return whether the class has been stored.																//OpenJ9-shared_classes_misc
     */																											//OpenJ9-shared_classes_misc
	private boolean storeClassIntoSharedClassesCache(Class<?> clazz, CodeSource cs, Manifest man, int index, LoadedModule module) {	//OpenJ9-shared_classes_misc
		boolean storeSuccessful = false;																//OpenJ9-shared_classes_misc
		if (usingSharedClasses()) {																		//OpenJ9-shared_classes_misc
			if (null == module) {																		//OpenJ9-shared_classes_misc
			// Check to see if we have already cached metadata for this index  							//OpenJ9-shared_classes_misc
				SharedClassMetaData metadata = sharedClassMetaDataCache.getSharedClassMetaData(index); 	//OpenJ9-shared_classes_misc
				// If we have not already cached the metadata for this index...   						//OpenJ9-shared_classes_misc
				if (metadata == null) {                                             					//OpenJ9-shared_classes_misc
					// ... create a new metadata entry                           						//OpenJ9-shared_classes_misc
					metadata = new SharedClassMetaData(cs, man);                    					//OpenJ9-shared_classes_misc
					// Cache the metadata for this index for future use         						//OpenJ9-shared_classes_misc
					sharedClassMetaDataCache.setSharedClassMetaData(index, metadata); 					//OpenJ9-shared_classes_misc
																										//OpenJ9-shared_classes_misc
				}                                                                   					//OpenJ9-shared_classes_misc
				// Store class in shared class cache for future use 									//OpenJ9-shared_classes_misc
				storeSuccessful = sharedClassServiceProvider.storeSharedClassURLClasspath(clazz, index);//OpenJ9-shared_classes_misc
			} else {																					//OpenJ9-shared_classes_misc
				// module has its own URL, store the class using the module URL							//OpenJ9-shared_classes_misc
				storeSuccessful = sharedClassServiceProvider.storeSharedClassURL(module.fileURL(), clazz);	//OpenJ9-shared_classes_misc
			}																						//OpenJ9-shared_classes_misc
		}																							//OpenJ9-shared_classes_misc
		return storeSuccessful;																		//OpenJ9-shared_classes_misc
	}																								//OpenJ9-shared_classes_misc

    /**
     * Defines the given binary class name to the VM, loading the class
     * bytes via the given Resource object.
     *
     * @return the resulting Class
     * @throws IOException if reading the resource fails
     * @throws SecurityException if there is a sealing violation (JAR spec)
     */
    private Class<?> defineClass(String cn, Resource res) throws IOException {
        URL url = res.getCodeSourceURL();
        Class<?> clazz = null;
        CodeSource cs = null;								//OpenJ9-shared_classes_misc
        Manifest man = null;								//OpenJ9-shared_classes_misc

        // if class is in a named package then ensure that the package is defined
        int pos = cn.lastIndexOf('.');
        if (pos != -1) {
            String pn = cn.substring(0, pos);
            man = res.getManifest();
            defineOrCheckPackage(pn, man, url);
        }

        // defines the class to the runtime
        ByteBuffer bb = res.getByteBuffer();
        if (bb != null) {
            CodeSigner[] signers = res.getCodeSigners();
            cs = new CodeSource(url, signers);
            clazz = defineClass(cn, bb, cs);
        } else {
            byte[] b = res.getBytes();
            CodeSigner[] signers = res.getCodeSigners();
            cs = new CodeSource(url, signers);
            clazz = defineClass(cn, b, 0, b.length, cs);
        }

        if (null != clazz) {																	//OpenJ9-shared_classes_misc
            //ignore the return value of storeClassIntoSharedClassesCache()						//OpenJ9-shared_classes_misc
            storeClassIntoSharedClassesCache(clazz, cs, man, res.getClasspathLoadIndex(), null);//OpenJ9-shared_classes_misc
        }																						//OpenJ9-shared_classes_misc
        return clazz;
    }


    // -- packages

    /**
     * Defines a package in this ClassLoader. If the package is already defined
     * then its sealing needs to be checked if sealed by the legacy sealing
     * mechanism.
     *
     * @throws SecurityException if there is a sealing violation (JAR spec)
     */
    protected Package defineOrCheckPackage(String pn, Manifest man, URL url) {
        Package pkg = getAndVerifyPackage(pn, man, url);
        if (pkg == null) {
            try {
                if (man != null) {
                    pkg = definePackage(pn, man, url);
                } else {
                    pkg = definePackage(pn, null, null, null, null, null, null, null);
                }
            } catch (IllegalArgumentException iae) {
                // defined by another thread so need to re-verify
                pkg = getAndVerifyPackage(pn, man, url);
                if (pkg == null)
                    throw new InternalError("Cannot find package: " + pn);
            }
        }
        return pkg;
    }

    /**
     * Gets the Package with the specified package name. If defined
     * then verifies it against the manifest and code source.
     *
     * @throws SecurityException if there is a sealing violation (JAR spec)
     */
    private Package getAndVerifyPackage(String pn, Manifest man, URL url) {
        Package pkg = getDefinedPackage(pn);
        if (pkg != null) {
            if (pkg.isSealed()) {
                if (!pkg.isSealed(url)) {
                    throw new SecurityException(
                        "sealing violation: package " + pn + " is sealed");
                }
            } else {
                // can't seal package if already defined without sealing
                if ((man != null) && isSealed(pn, man)) {
                    throw new SecurityException(
                        "sealing violation: can't seal package " + pn +
                        ": already defined");
                }
            }
        }
        return pkg;
    }

    /**
     * Defines a new package in this ClassLoader. The attributes in the specified
     * Manifest are used to get the package version and sealing information.
     *
     * @throws IllegalArgumentException if the package name duplicates an
     *      existing package either in this class loader or one of its ancestors
     * @throws SecurityException if the package name is untrusted in the manifest
     */
    private Package definePackage(String pn, Manifest man, URL url) {
        String specTitle = null;
        String specVersion = null;
        String specVendor = null;
        String implTitle = null;
        String implVersion = null;
        String implVendor = null;
        String sealed = null;
        URL sealBase = null;

        if (man != null) {
            Attributes attr = SharedSecrets.javaUtilJarAccess()
                    .getTrustedAttributes(man, pn.replace('.', '/').concat("/"));
            if (attr != null) {
                specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
                specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
                specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
                implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
                implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
                sealed = attr.getValue(Attributes.Name.SEALED);
            }

            attr = man.getMainAttributes();
            if (attr != null) {
                if (specTitle == null)
                    specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
                if (specVersion == null)
                    specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
                if (specVendor == null)
                    specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
                if (implTitle == null)
                    implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
                if (implVersion == null)
                    implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                if (implVendor == null)
                    implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
                if (sealed == null)
                    sealed = attr.getValue(Attributes.Name.SEALED);
            }

            // package is sealed
            if ("true".equalsIgnoreCase(sealed))
                sealBase = url;
        }
        return definePackage(pn,
                specTitle,
                specVersion,
                specVendor,
                implTitle,
                implVersion,
                implVendor,
                sealBase);
    }

    /**
     * Returns {@code true} if the specified package name is sealed according to
     * the given manifest.
     *
     * @throws SecurityException if the package name is untrusted in the manifest
     */
    private boolean isSealed(String pn, Manifest man) {
        Attributes attr = SharedSecrets.javaUtilJarAccess()
                .getTrustedAttributes(man, pn.replace('.', '/').concat("/"));
        String sealed = null;
        if (attr != null)
            sealed = attr.getValue(Attributes.Name.SEALED);
        if (sealed == null && (attr = man.getMainAttributes()) != null)
            sealed = attr.getValue(Attributes.Name.SEALED);
        return "true".equalsIgnoreCase(sealed);
    }

    // -- miscellaneous supporting methods

    /**
     * Returns the ModuleReader for the given module, creating it if needed.
     */
    private ModuleReader moduleReaderFor(ModuleReference mref) {
        ModuleReader reader = moduleToReader.get(mref);
        if (reader == null) {
            // avoid method reference during startup
            Function<ModuleReference, ModuleReader> create = new Function<>() {
                public ModuleReader apply(ModuleReference moduleReference) {
                    try {
                        return mref.open();
                    } catch (IOException e) {
                        // Return a null module reader to avoid a future class
                        // load attempting to open the module again.
                        return new NullModuleReader();
                    }
                }
            };
            reader = moduleToReader.computeIfAbsent(mref, create);
        }
        return reader;
    }

    /**
     * A ModuleReader that doesn't read any resources.
     */
    private static class NullModuleReader implements ModuleReader {
        @Override
        public Optional<URI> find(String name) {
            return Optional.empty();
        }
        @Override
        public Stream<String> list() {
            return Stream.empty();
        }
        @Override
        public void close() {
            throw new InternalError("Should not get here");
        }
    };

    /**
     * Returns true if the given module opens the given package
     * unconditionally.
     *
     * @implNote This method currently iterates over each of the open
     * packages. This will be replaced once the ModuleDescriptor.Opens
     * API is updated.
     */
    private boolean isOpen(ModuleReference mref, String pn) {
        ModuleDescriptor descriptor = mref.descriptor();
        if (descriptor.isOpen() || descriptor.isAutomatic())
            return true;
        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
            String source = opens.source();
            if (!opens.isQualified() && source.equals(pn)) {
                return true;
            }
        }
        return false;
    }

    // Called from VM only, during -Xshare:dump
    private void resetArchivedStates() {
        ucp = null;
        resourceCache = null;
        if (!moduleToReader.isEmpty()) {
            moduleToReader.clear();
        }
    }
}
