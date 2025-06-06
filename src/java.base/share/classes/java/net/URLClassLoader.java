/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2018, 2025 All Rights Reserved
 * ===========================================================================
 */

package java.net;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PermissionCollection;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import jdk.internal.loader.Resource;
import jdk.internal.loader.URLClassPath;
import jdk.internal.access.SharedSecrets;
import jdk.internal.perf.PerfCounter;

import com.ibm.sharedclasses.spi.SharedClassProvider;

/**
 * This class loader is used to load classes and resources from a search
 * path of URLs referring to both JAR files and directories. Any {@code jar:}
 * scheme URL (see {@link java.net.JarURLConnection}) is assumed to refer to a
 * JAR file.  Any {@code file:} scheme URL that ends with a '/' is assumed to
 * refer to a directory. Otherwise, the URL is assumed to refer to a JAR file
 * which will be opened as needed.
 * <p>
 * This class loader supports the loading of classes and resources from the
 * contents of a <a href="../util/jar/JarFile.html#multirelease">multi-release</a>
 * JAR file that is referred to by a given URL.
 *
 * @author  David Connelly
 * @since   1.2
 */
public class URLClassLoader extends SecureClassLoader implements Closeable {
    /* The search path for classes and resources */
    private final URLClassPath ucp;

    /* Private member fields used for shared classes. */                         //OpenJ9-shared_classes_misc
    private SharedClassProvider sharedClassServiceProvider;                      //OpenJ9-shared_classes_misc
    private SharedClassMetaDataCache sharedClassMetaDataCache;                   //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
    /*                                                                           //OpenJ9-shared_classes_misc
     * Wrapper class for maintaining the index of where the metadata (code       //OpenJ9-shared_classes_misc
     * source and manifest) is found - used only in shared classes context.      //OpenJ9-shared_classes_misc
     */                                                                          //OpenJ9-shared_classes_misc
    private static final class SharedClassIndexHolder {                          //OpenJ9-shared_classes_misc
        int index;                                                               //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
        public void setIndex(int index) {                                        //OpenJ9-shared_classes_misc
            this.index = index;                                                  //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
    }                                                                            //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
    /*                                                                           //OpenJ9-shared_classes_misc
     * Wrapper class for internal storage of metadata (code source and manifest) //OpenJ9-shared_classes_misc
     * associated with shared class - used only in shared classes context.       //OpenJ9-shared_classes_misc
     */                                                                          //OpenJ9-shared_classes_misc
    private static final class SharedClassMetaData {                             //OpenJ9-shared_classes_misc
        private final CodeSource codeSource;                                     //OpenJ9-shared_classes_misc
        private final Manifest manifest;                                         //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
        SharedClassMetaData(CodeSource codeSource, Manifest manifest) {          //OpenJ9-shared_classes_misc
            this.codeSource = codeSource;                                        //OpenJ9-shared_classes_misc
            this.manifest = manifest;                                            //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
        public CodeSource getCodeSource() { return codeSource; }                 //OpenJ9-shared_classes_misc
        public Manifest getManifest() { return manifest; }                       //OpenJ9-shared_classes_misc
    }                                                                            //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
    /*                                                                           //OpenJ9-shared_classes_misc
     * Represents a collection of SharedClassMetaData objects retrievable by     //OpenJ9-shared_classes_misc
     * index.                                                                    //OpenJ9-shared_classes_misc
     */                                                                          //OpenJ9-shared_classes_misc
    private static final class SharedClassMetaDataCache {                        //OpenJ9-shared_classes_misc
        private static final int BLOCKSIZE = 10;                                 //OpenJ9-shared_classes_misc
        private SharedClassMetaData[] store;                                     //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
        public SharedClassMetaDataCache(int initialSize) {                       //OpenJ9-shared_classes_misc
            /* Allocate space for an initial amount of metadata entries */       //OpenJ9-shared_classes_misc
            store = new SharedClassMetaData[initialSize];                        //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
        /**                                                                      //OpenJ9-shared_classes_misc
         * Retrieves the SharedClassMetaData stored at the given index, or null  //OpenJ9-shared_classes_misc
         * if no SharedClassMetaData was previously stored at the given index    //OpenJ9-shared_classes_misc
         * or the index is out of range.                                         //OpenJ9-shared_classes_misc
         */                                                                      //OpenJ9-shared_classes_misc
        public synchronized SharedClassMetaData getSharedClassMetaData(int index) { //OpenJ9-shared_classes_misc
            if (index < 0 || store.length <= index) {                            //OpenJ9-shared_classes_misc
                return null;                                                     //OpenJ9-shared_classes_misc
            }                                                                    //OpenJ9-shared_classes_misc
            return store[index];                                                 //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
        /**                                                                      //OpenJ9-shared_classes_misc
         * Stores the supplied SharedClassMetaData at the given index in the     //OpenJ9-shared_classes_misc
         * store. The store will be grown to contain the index if necessary.     //OpenJ9-shared_classes_misc
         */                                                                      //OpenJ9-shared_classes_misc
        public synchronized void setSharedClassMetaData(int index,               //OpenJ9-shared_classes_misc
                                                     SharedClassMetaData data) { //OpenJ9-shared_classes_misc
            ensureSize(index);                                                   //OpenJ9-shared_classes_misc
            store[index] = data;                                                 //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
        /* Ensure that the store can hold at least index number of entries */    //OpenJ9-shared_classes_misc
        private synchronized void ensureSize(int index) {                        //OpenJ9-shared_classes_misc
            if (store.length <= index) {                                         //OpenJ9-shared_classes_misc
                int newSize = (index + BLOCKSIZE);                               //OpenJ9-shared_classes_misc
                SharedClassMetaData[] newSCMDS = new SharedClassMetaData[newSize]; //OpenJ9-shared_classes_misc
                System.arraycopy(store, 0, newSCMDS, 0, store.length);           //OpenJ9-shared_classes_misc
                store = newSCMDS;                                                //OpenJ9-shared_classes_misc
            }                                                                    //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
    }                                                                            //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
    /*                                                                           //OpenJ9-shared_classes_misc
     * Return true if shared classes support is active, otherwise false.         //OpenJ9-shared_classes_misc
     */                                                                          //OpenJ9-shared_classes_misc
    private boolean usingSharedClasses() {                                       //OpenJ9-shared_classes_misc
        return (sharedClassServiceProvider != null);                             //OpenJ9-shared_classes_misc
    }                                                                            //OpenJ9-shared_classes_misc
                                                                                 //OpenJ9-shared_classes_misc
    /*                                                                           //OpenJ9-shared_classes_misc
     * Initialize support for shared classes.                                    //OpenJ9-shared_classes_misc
     */                                                                          //OpenJ9-shared_classes_misc
    private synchronized void initializeSharedClassesSupport(URL[] initialClassPath) { //OpenJ9-shared_classes_misc
       if (null == sharedClassServiceProvider) {                                 //OpenJ9-shared_classes_misc
            ServiceLoader<SharedClassProvider> sl = ServiceLoader.load(SharedClassProvider.class); //OpenJ9-shared_classes_misc
            for (SharedClassProvider p : sl) {                                   //OpenJ9-shared_classes_misc
                if (null != p) {                                                 //OpenJ9-shared_classes_misc
                    if (null != p.initializeProvider(this, initialClassPath, false, false)) { //OpenJ9-shared_classes_misc
                        sharedClassServiceProvider = p;                          //OpenJ9-shared_classes_misc
                        break;                                                   //OpenJ9-shared_classes_misc
                    }                                                            //OpenJ9-shared_classes_misc
                }                                                                //OpenJ9-shared_classes_misc
            }                                                                    //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
        if (usingSharedClasses()) {                                              //OpenJ9-shared_classes_misc
            /* Create a metadata cache */                                        //OpenJ9-shared_classes_misc
            this.sharedClassMetaDataCache = new SharedClassMetaDataCache(initialClassPath.length); //OpenJ9-shared_classes_misc
        }                                                                        //OpenJ9-shared_classes_misc
    }                                                                            //OpenJ9-shared_classes_misc

    /**
     * Constructs a new URLClassLoader for the given URLs. The URLs will be
     * searched in the order specified for classes and resources after first
     * searching in the specified parent class loader.  Any {@code jar:}
     * scheme URL is assumed to refer to a JAR file.  Any {@code file:} scheme
     * URL that ends with a '/' is assumed to refer to a directory.  Otherwise,
     * the URL is assumed to refer to a JAR file which will be downloaded and
     * opened as needed.
     *
     * @apiNote If {@code parent} is specified as {@code null} (for the
     * bootstrap class loader) then there is no guarantee that all platform
     * classes are visible.
     * See {@linkplain ClassLoader##builtinLoaders Run-time Built-in Class Loaders}
     * for information on the bootstrap class loader and other built-in class loaders.
     *
     * @param      urls the URLs from which to load classes and resources
     * @param      parent the parent class loader for delegation, can be {@code null}
     *                    for the bootstrap class loader
     * @throws     NullPointerException if {@code urls} or any of its
     *             elements is {@code null}.
     */
    public URLClassLoader(URL[] urls, ClassLoader parent) {
        super(parent);
        initializeSharedClassesSupport(urls);                                //OpenJ9-shared_classes_misc
        this.ucp = new URLClassPath(urls, null, sharedClassServiceProvider); //OpenJ9-shared_classes_misc
    }

    /**
     * Constructs a new URLClassLoader for the specified URLs using the
     * {@linkplain ClassLoader#getSystemClassLoader() system class loader
     * as the parent}. The URLs will be searched in the order
     * specified for classes and resources after first searching in the
     * parent class loader. Any URL that ends with a '/' is assumed to
     * refer to a directory. Otherwise, the URL is assumed to refer to
     * a JAR file which will be downloaded and opened as needed.
     *
     * @param      urls the URLs from which to load classes and resources
     *
     * @throws     NullPointerException if {@code urls} or any of its
     *             elements is {@code null}.
     */
    public URLClassLoader(URL[] urls) {
        super();
        initializeSharedClassesSupport(urls);                                //OpenJ9-shared_classes_misc
        this.ucp = new URLClassPath(urls, null, sharedClassServiceProvider); //OpenJ9-shared_classes_misc
    }

    /**
     * Constructs a new URLClassLoader for the specified URLs, parent
     * class loader, and URLStreamHandlerFactory. The parent argument
     * will be used as the parent class loader for delegation. The
     * factory argument will be used as the stream handler factory to
     * obtain protocol handlers when creating new jar URLs.
     *
     * @apiNote If {@code parent} is specified as {@code null} (for the
     * bootstrap class loader) then there is no guarantee that all platform
     * classes are visible.
     * See {@linkplain ClassLoader##builtinLoaders Run-time Built-in Class Loaders}
     * for information on the bootstrap class loader and other built-in class loaders.
     *
     * @param  urls the URLs from which to load classes and resources
     * @param  parent the parent class loader for delegation, can be {@code null}
     *                for the bootstrap class loader
     * @param  factory the URLStreamHandlerFactory to use when creating URLs
     *
     * @throws NullPointerException if {@code urls} or any of its
     *         elements is {@code null}.
     */
    public URLClassLoader(URL[] urls, ClassLoader parent,
                          URLStreamHandlerFactory factory) {
        super(parent);
        initializeSharedClassesSupport(urls);                                   //OpenJ9-shared_classes_misc
        this.ucp = new URLClassPath(urls, factory, sharedClassServiceProvider); //OpenJ9-shared_classes_misc
    }


    /**
     * Constructs a new named {@code URLClassLoader} for the specified URLs.
     * The URLs will be searched in the order specified for classes
     * and resources after first searching in the specified parent class loader.
     * Any URL that ends with a '/' is assumed to refer to a directory.
     * Otherwise, the URL is assumed to refer to a JAR file which will be
     * downloaded and opened as needed.
     *
     * @apiNote If {@code parent} is specified as {@code null} (for the
     * bootstrap class loader) then there is no guarantee that all platform
     * classes are visible.
     * See {@linkplain ClassLoader##builtinLoaders Run-time Built-in Class Loaders}
     * for information on the bootstrap class loader and other built-in class loaders.
     *
     * @param  name class loader name; or {@code null} if not named
     * @param  urls the URLs from which to load classes and resources
     * @param  parent the parent class loader for delegation, can be {@code null}
     *                for the bootstrap class loader
     *
     * @throws IllegalArgumentException if the given name is empty.
     * @throws NullPointerException if {@code urls} or any of its
     *         elements is {@code null}.
     *
     * @since 9
     */
    public URLClassLoader(String name,
                          URL[] urls,
                          ClassLoader parent) {
        super(name, parent);
        initializeSharedClassesSupport(urls);                                //OpenJ9-shared_classes_misc
        this.ucp = new URLClassPath(urls, null, sharedClassServiceProvider); //OpenJ9-shared_classes_misc
    }

    /**
     * Constructs a new named {@code URLClassLoader} for the specified URLs,
     * parent class loader, and URLStreamHandlerFactory.
     * The parent argument will be used as the parent class loader for delegation.
     * The factory argument will be used as the stream handler factory to
     * obtain protocol handlers when creating new jar URLs.
     *
     * @apiNote If {@code parent} is specified as {@code null} (for the
     * bootstrap class loader) then there is no guarantee that all platform
     * classes are visible.
     * See {@linkplain ClassLoader##builtinLoaders Run-time Built-in Class Loaders}
     * for information on the bootstrap class loader and other built-in class loaders.
     *
     * @param  name class loader name; or {@code null} if not named
     * @param  urls the URLs from which to load classes and resources
     * @param  parent the parent class loader for delegation, can be {@code null}
     *                for the bootstrap class loader
     * @param  factory the URLStreamHandlerFactory to use when creating URLs
     *
     * @throws IllegalArgumentException if the given name is empty.
     * @throws NullPointerException if {@code urls} or any of its
     *         elements is {@code null}.
     *
     * @since 9
     */
    public URLClassLoader(String name, URL[] urls, ClassLoader parent,
                          URLStreamHandlerFactory factory) {
        super(name, parent);
        initializeSharedClassesSupport(urls);                                   //OpenJ9-shared_classes_misc
        this.ucp = new URLClassPath(urls, factory, sharedClassServiceProvider); //OpenJ9-shared_classes_misc
    }

    /* A map (used as a set) to keep track of closeable local resources
     * (either JarFiles or FileInputStreams). We don't care about
     * Http resources since they don't need to be closed.
     *
     * If the resource is coming from a jar file
     * we keep a (weak) reference to the JarFile object which can
     * be closed if URLClassLoader.close() called. Due to jar file
     * caching there will typically be only one JarFile object
     * per underlying jar file.
     *
     * For file resources, which is probably a less common situation
     * we have to keep a weak reference to each stream.
     */

    private final WeakHashMap<Closeable,Void>
        closeables = new WeakHashMap<>();

    /**
     * Returns an input stream for reading the specified resource.
     * If this loader is closed, then any resources opened by this method
     * will be closed.
     *
     * <p> The search order is described in the documentation for {@link
     * #getResource(String)}.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  An input stream for reading the resource, or {@code null}
     *          if the resource could not be found
     *
     * @throws  NullPointerException If {@code name} is {@code null}
     *
     * @since  1.7
     */
    public InputStream getResourceAsStream(String name) {
        Objects.requireNonNull(name);
        URL url = getResource(name);
        try {
            if (url == null) {
                return null;
            }
            URLConnection urlc = url.openConnection();
            InputStream is = urlc.getInputStream();
            if (urlc instanceof JarURLConnection juc) {
                JarFile jar = juc.getJarFile();
                synchronized (closeables) {
                    if (!closeables.containsKey(jar)) {
                        closeables.put(jar, null);
                    }
                }
            } else if (urlc instanceof sun.net.www.protocol.file.FileURLConnection) {
                synchronized (closeables) {
                    closeables.put(is, null);
                }
            }
            return is;
        } catch (IOException e) {
            return null;
        }
    }

   /**
    * Closes this URLClassLoader, so that it can no longer be used to load
    * new classes or resources that are defined by this loader.
    * Classes and resources defined by any of this loader's parents in the
    * delegation hierarchy are still accessible. Also, any classes or resources
    * that are already loaded, are still accessible.
    * <p>
    * In the case of jar: and file: URLs, it also closes any files
    * that were opened by it. If another thread is loading a
    * class when the {@code close} method is invoked, then the result of
    * that load is undefined.
    * <p>
    * The method makes a best effort attempt to close all opened files,
    * by catching {@link IOException}s internally. Unchecked exceptions
    * and errors are not caught. Calling close on an already closed
    * loader has no effect.
    *
    * @throws    IOException if closing any file opened by this class loader
    * resulted in an IOException. Any such exceptions are caught internally.
    * If only one is caught, then it is re-thrown. If more than one exception
    * is caught, then the second and following exceptions are added
    * as suppressed exceptions of the first one caught, which is then re-thrown.
    *
    * @since 1.7
    */
    public void close() throws IOException {
        List<IOException> errors = ucp.closeLoaders();

        // now close any remaining streams.

        synchronized (closeables) {
            Set<Closeable> keys = closeables.keySet();
            for (Closeable c : keys) {
                try {
                    c.close();
                } catch (IOException ioex) {
                    errors.add(ioex);
                }
            }
            closeables.clear();
        }

        if (errors.isEmpty()) {
            return;
        }

        IOException firstex = errors.remove(0);

        // Suppress any remaining exceptions

        for (IOException error: errors) {
            firstex.addSuppressed(error);
        }
        throw firstex;
    }

    /**
     * Appends the specified URL to the list of URLs to search for
     * classes and resources.
     * <p>
     * If the URL specified is {@code null} or is already in the
     * list of URLs, or if this loader is closed, then invoking this
     * method has no effect.
     *
     * @param url the URL to be added to the search path of URLs
     */
    protected void addURL(URL url) {
        ucp.addURL(url);
    }

    /**
     * Returns the search path of URLs for loading classes and resources.
     * This includes the original list of URLs specified to the constructor,
     * along with any URLs subsequently appended by the addURL() method.
     * @return the search path of URLs for loading classes and resources.
     */
    public URL[] getURLs() {
        return ucp.getURLs();
    }

    /**
     * Finds and loads the class with the specified name from the URL search
     * path. Any URLs referring to JAR files are loaded and opened as needed
     * until the class is found.
     *
     * @param     name the name of the class
     * @return    the resulting class
     * @throws    ClassNotFoundException if the class could not be found,
     *            or if the loader is closed.
     * @throws    NullPointerException if {@code name} is {@code null}.
     */
    protected Class<?> findClass(final String name)
        throws ClassNotFoundException
    {
        /* Try to find the class from the shared cache using the class name. //OpenJ9-shared_classes_misc
         * If we found the class and if we have its corresponding metadata  //OpenJ9-shared_classes_misc
         * (code source and manifest entry) already cached, then we define  //OpenJ9-shared_classes_misc
         * the class passing in these parameters.  If however, we do not    //OpenJ9-shared_classes_misc
         * have the metadata cached, then we define the class as normal.    //OpenJ9-shared_classes_misc
         * Also, if we do not find the class from the shared class cache,   //OpenJ9-shared_classes_misc
         * we define the class as normal.                                   //OpenJ9-shared_classes_misc
         */                                                                 //OpenJ9-shared_classes_misc
        if (usingSharedClasses()) {                                         //OpenJ9-shared_classes_misc
            SharedClassIndexHolder sharedClassIndexHolder = new SharedClassIndexHolder(); /*ibm@94142*/ //OpenJ9-shared_classes_misc
            IntConsumer consumer = (i)->sharedClassIndexHolder.setIndex(i); //OpenJ9-shared_classes_misc
            byte[] sharedClazz = sharedClassServiceProvider.findSharedClassURLClasspath(name, consumer); //OpenJ9-shared_classes_misc
            if (sharedClazz != null) {                                      //OpenJ9-shared_classes_misc
                int indexFoundData = sharedClassIndexHolder.index;          //OpenJ9-shared_classes_misc
                SharedClassMetaData metadata = sharedClassMetaDataCache.getSharedClassMetaData(indexFoundData); //OpenJ9-shared_classes_misc
                if (metadata != null) {                                     //OpenJ9-shared_classes_misc
                    try {                                                   //OpenJ9-shared_classes_misc
                        Class<?> clazz = defineClass(name, sharedClazz,     //OpenJ9-shared_classes_misc
                                metadata.getCodeSource(),                   //OpenJ9-shared_classes_misc
                                metadata.getManifest());                    //OpenJ9-shared_classes_misc
                        return clazz;                                       //OpenJ9-shared_classes_misc
                    } catch (IOException e) {                               //OpenJ9-shared_classes_misc
                        e.printStackTrace();                                //OpenJ9-shared_classes_misc
                    }                                                       //OpenJ9-shared_classes_misc
                }                                                           //OpenJ9-shared_classes_misc
            }                                                               //OpenJ9-shared_classes_misc
        }                                                                   //OpenJ9-shared_classes_misc
        String path = name.replace('.', '/').concat(".class");
        Resource res = ucp.getResource(path);
        if (res != null) {
            try {
                return defineClass(name, res);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            } catch (ClassFormatError e2) {
                if (res.getDataError() != null) {
                    e2.addSuppressed(res.getDataError());
                }
                throw e2;
            }
        }
        throw new ClassNotFoundException(name);
    }

    /*
     * Retrieve the package using the specified package name.
     * If non-null, verify the package using the specified code
     * source and manifest.
     */
    private Package getAndVerifyPackage(String pkgname,
                                        Manifest man, URL url) {
        Package pkg = getDefinedPackage(pkgname);
        if (pkg != null) {
            // Package found, so check package sealing.
            if (pkg.isSealed()) {
                // Verify that code source URL is the same.
                if (!pkg.isSealed(url)) {
                    throw new SecurityException(
                        "sealing violation: package " + pkgname + " is sealed");
                }
            } else {
                // Make sure we are not attempting to seal the package
                // at this code source URL.
                if ((man != null) && isSealed(pkgname, man)) {
                    throw new SecurityException(
                        "sealing violation: can't seal package " + pkgname +
                        ": already loaded");
                }
            }
        }
        return pkg;
    }

    /*
     * Defines a Class using the class bytes obtained from the specified
     * Resource. The resulting Class must be resolved before it can be
     * used.
     */
    private Class<?> defineClass(String name, Resource res) throws IOException {
        Class<?> clazz;                                                        //OpenJ9-shared_classes_misc
        CodeSource cs;                                                         //OpenJ9-shared_classes_misc
        Manifest man = null;                                                   //OpenJ9-shared_classes_misc
        long t0 = System.nanoTime();
        int i = name.lastIndexOf('.');
        URL url = res.getCodeSourceURL();
        if (i != -1) {
            String pkgname = name.substring(0, i);
            // Check if package already loaded.
            man = res.getManifest();                                           //OpenJ9-shared_classes_misc
            if (getAndVerifyPackage(pkgname, man, url) == null) {
                try {
                    if (man != null) {
                        definePackage(pkgname, man, url);
                    } else {
                        definePackage(pkgname, null, null, null, null, null, null, null);
                    }
                } catch (IllegalArgumentException iae) {
                    // parallel-capable class loaders: re-verify in case of a
                    // race condition
                    if (getAndVerifyPackage(pkgname, man, url) == null) {
                        // Should never happen
                        throw new AssertionError("Cannot find package " +
                                                 pkgname);
                    }
                }
            }
        }
        // Now read the class bytes and define the class
        java.nio.ByteBuffer bb = res.getByteBuffer();
        if (bb != null) {
            // Use (direct) ByteBuffer:
            CodeSigner[] signers = res.getCodeSigners();
            cs = new CodeSource(url, signers);
            clazz = defineClass(name, bb, cs);
        } else {
            byte[] b = res.getBytes();
            // must read certificates AFTER reading bytes.
            CodeSigner[] signers = res.getCodeSigners();
            cs = new CodeSource(url, signers);
            clazz = defineClass(name, b, 0, b.length, cs);
        }
        /*                                                                      //OpenJ9-shared_classes_misc
         * Since we have already stored the class path index (of where this     //OpenJ9-shared_classes_misc
         * resource came from), we can retrieve it here.  The storing is done   //OpenJ9-shared_classes_misc
         * in getResource() in URLClassPath.java.  The index is the specified   //OpenJ9-shared_classes_misc
         * position in the URL search path (see getLoader()).  The              //OpenJ9-shared_classes_misc
         * storeSharedClass() call below, stores the class in the shared class  //OpenJ9-shared_classes_misc
         * cache for future use.                                                //OpenJ9-shared_classes_misc
         */                                                                     //OpenJ9-shared_classes_misc
        if (usingSharedClasses()) {                                             //OpenJ9-shared_classes_misc
            /* Determine the index into the search path for this class */       //OpenJ9-shared_classes_misc
            int index = res.getClasspathLoadIndex();                            //OpenJ9-shared_classes_misc
            /* Check to see if we have already cached metadata for this index. */ //OpenJ9-shared_classes_misc
            SharedClassMetaData metadata = sharedClassMetaDataCache.getSharedClassMetaData(index); //OpenJ9-shared_classes_misc
            /* If we have not already cached the metadata for this index... */  //OpenJ9-shared_classes_misc
            if (metadata == null) {                                             //OpenJ9-shared_classes_misc
                /* ... create a new metadata entry */                           //OpenJ9-shared_classes_misc
                metadata = new SharedClassMetaData(cs, man);                    //OpenJ9-shared_classes_misc
                /* Cache the metadata for this index for future use. */         //OpenJ9-shared_classes_misc
                sharedClassMetaDataCache.setSharedClassMetaData(index, metadata); //OpenJ9-shared_classes_misc
            }                                                                   //OpenJ9-shared_classes_misc
            try {                                                               //OpenJ9-shared_classes_misc
                /* Store class in shared class cache for future use. */         //OpenJ9-shared_classes_misc
                sharedClassServiceProvider.storeSharedClassURLClasspath(clazz, index); //OpenJ9-shared_classes_misc
            } catch (Exception e) {                                             //OpenJ9-shared_classes_misc
                e.printStackTrace();                                            //OpenJ9-shared_classes_misc
            }                                                                   //OpenJ9-shared_classes_misc
        }                                                                       //OpenJ9-shared_classes_misc
        return clazz;                                                           //OpenJ9-shared_classes_misc
    }

    /*                                                                          //OpenJ9-shared_classes_misc
     * Defines a class using the class bytes, code source and manifest          //OpenJ9-shared_classes_misc
     * obtained from the specified shared class cache. The resulting            //OpenJ9-shared_classes_misc
     * class must be resolved before it can be used.  This method is            //OpenJ9-shared_classes_misc
     * used only in a shared classes context.                                   //OpenJ9-shared_classes_misc
     */                                                                         //OpenJ9-shared_classes_misc
    private Class<?> defineClass(String name, byte[] sharedClazz, CodeSource codesource, Manifest man) throws IOException { //OpenJ9-shared_classes_misc
       int i = name.lastIndexOf('.');                                          //OpenJ9-shared_classes_misc
       URL url = codesource.getLocation();                                     //OpenJ9-shared_classes_misc
       if (i != -1) {                                                          //OpenJ9-shared_classes_misc
           String pkgname = name.substring(0, i);                              //OpenJ9-shared_classes_misc
           // Check if package already loaded.                                 //OpenJ9-shared_classes_misc
           if (getAndVerifyPackage(pkgname, man, url) == null) {               //OpenJ9-shared_classes_misc
               try {                                                           //OpenJ9-shared_classes_misc
                   if (null != man) {                                          //OpenJ9-shared_classes_misc
                      definePackage(pkgname, man, url);                        //OpenJ9-shared_classes_misc
                   } else {                                                    //OpenJ9-shared_classes_misc
                      definePackage(pkgname, null, null, null, null, null, null, null); //OpenJ9-shared_classes_misc
                    }                                                          //OpenJ9-shared_classes_misc
                } catch (IllegalArgumentException iae) {                       //OpenJ9-shared_classes_misc
                    // https://github.com/eclipse-openj9/openj9/issues/3038    //OpenJ9-shared_classes_misc
                    // Detect and ignore race between two threads defining different classes in the same package. //OpenJ9-shared_classes_misc
                    if (getAndVerifyPackage(pkgname, man, url) == null) {      //OpenJ9-shared_classes_misc
                        // Should never happen                                 //OpenJ9-shared_classes_misc
                        throw new AssertionError("Cannot find package " + pkgname); //OpenJ9-shared_classes_misc
                    }                                                          //OpenJ9-shared_classes_misc
                }                                                              //OpenJ9-shared_classes_misc
           }                                                                   //OpenJ9-shared_classes_misc
       }                                                                       //OpenJ9-shared_classes_misc
       /*                                                                      //OpenJ9-shared_classes_misc
        * Now read the class bytes and define the class.  We don't need to call //OpenJ9-shared_classes_misc
        * storeSharedClass(), since its already in our shared class cache.     //OpenJ9-shared_classes_misc
        */                                                                     //OpenJ9-shared_classes_misc
       return defineClass(name, sharedClazz, 0, sharedClazz.length, codesource); //OpenJ9-shared_classes_misc
     }                                                                         //OpenJ9-shared_classes_misc

    /**
     * Defines a new package by name in this {@code URLClassLoader}.
     * The attributes contained in the specified {@code Manifest}
     * will be used to obtain package version and sealing information.
     * For sealed packages, the additional URL specifies the code source URL
     * from which the package was loaded.
     *
     * @param name  the package name
     * @param man   the {@code Manifest} containing package version and sealing
     *              information
     * @param url   the code source url for the package, or null if none
     * @throws      IllegalArgumentException if the package name is
     *              already defined by this class loader
     * @return      the newly defined {@code Package} object
     */
    protected Package definePackage(String name, Manifest man, URL url) {
        String specTitle = null, specVersion = null, specVendor = null;
        String implTitle = null, implVersion = null, implVendor = null;
        String sealed = null;
        URL sealBase = null;

        Attributes attr = SharedSecrets.javaUtilJarAccess()
                .getTrustedAttributes(man, name.replace('.', '/').concat("/"));
        if (attr != null) {
            specTitle   = attr.getValue(Name.SPECIFICATION_TITLE);
            specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
            specVendor  = attr.getValue(Name.SPECIFICATION_VENDOR);
            implTitle   = attr.getValue(Name.IMPLEMENTATION_TITLE);
            implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
            implVendor  = attr.getValue(Name.IMPLEMENTATION_VENDOR);
            sealed      = attr.getValue(Name.SEALED);
        }
        attr = man.getMainAttributes();
        if (attr != null) {
            if (specTitle == null) {
                specTitle = attr.getValue(Name.SPECIFICATION_TITLE);
            }
            if (specVersion == null) {
                specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
            }
            if (specVendor == null) {
                specVendor = attr.getValue(Name.SPECIFICATION_VENDOR);
            }
            if (implTitle == null) {
                implTitle = attr.getValue(Name.IMPLEMENTATION_TITLE);
            }
            if (implVersion == null) {
                implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
            }
            if (implVendor == null) {
                implVendor = attr.getValue(Name.IMPLEMENTATION_VENDOR);
            }
            if (sealed == null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        if ("true".equalsIgnoreCase(sealed)) {
            sealBase = url;
        }
        return definePackage(name, specTitle, specVersion, specVendor,
                             implTitle, implVersion, implVendor, sealBase);
    }

    /*
     * Returns true if the specified package name is sealed according to the
     * given manifest.
     *
     * @throws SecurityException if the package name is untrusted in the manifest
     */
    private boolean isSealed(String name, Manifest man) {
        Attributes attr = SharedSecrets.javaUtilJarAccess()
                .getTrustedAttributes(man, name.replace('.', '/').concat("/"));
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }

    /**
     * Finds the resource with the specified name on the URL search path.
     *
     * @param name the name of the resource
     * @return a {@code URL} for the resource, or {@code null}
     * if the resource could not be found, or if the loader is closed.
     */
    public URL findResource(final String name) {
        return ucp.findResource(name);
    }

    /**
     * Returns an Enumeration of URLs representing all of the resources
     * on the URL search path having the specified name.
     *
     * @param name the resource name
     * @throws    IOException if an I/O exception occurs
     * @return An {@code Enumeration} of {@code URL}s.
     *         If the loader is closed, the Enumeration contains no elements.
     */
    @Override
    public Enumeration<URL> findResources(final String name)
        throws IOException
    {
        final Enumeration<URL> e = ucp.findResources(name);

        return new Enumeration<>() {
            private URL url = null;

            private boolean next() {
                if (url != null) {
                    return true;
                }
                if (!e.hasMoreElements()) {
                    return false;
                }
                url = e.nextElement();
                return url != null;
            }

            @Override
            public URL nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                URL u = url;
                url = null;
                return u;
            }

            @Override
            public boolean hasMoreElements() {
                return next();
            }
        };
    }

    /**
     * {@return an {@linkplain PermissionCollection empty Permission collection}}
     *
     * @param codesource the {@code CodeSource}
     * @throws NullPointerException if {@code codesource} is {@code null}.
     */
    @Override
    protected PermissionCollection getPermissions(CodeSource codesource) {
        Objects.requireNonNull(codesource);
        return new Permissions();
    }

    /**
     * Creates a new instance of URLClassLoader for the specified
     * URLs and parent class loader.
     *
     * @param urls the URLs to search for classes and resources
     * @param parent the parent class loader for delegation
     * @throws     NullPointerException if {@code urls} or any of its
     *             elements is {@code null}.
     * @return the resulting class loader
     */
    public static URLClassLoader newInstance(final URL[] urls,
                                             final ClassLoader parent) {
        return new URLClassLoader(null, urls, parent);
    }

    /**
     * Creates a new instance of URLClassLoader for the specified
     * URLs and default parent class loader.
     *
     * @param urls the URLs to search for classes and resources
     * @throws     NullPointerException if {@code urls} or any of its
     *             elements is {@code null}.
     * @return the resulting class loader
     */
    public static URLClassLoader newInstance(final URL[] urls) {
        return new URLClassLoader(urls);
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}
