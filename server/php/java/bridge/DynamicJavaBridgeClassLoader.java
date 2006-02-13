/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

/**
 * The bridge class loader which uses the DynamicClassLoader when possible.
 * @author jostb
 *
 */
/*  
 * Instances of this class are shared. 
 * Use the JavaBridgeClassLoader if you need to access the current request-handling bridge instance. 
 * */
public class DynamicJavaBridgeClassLoader extends DynamicClassLoader {

    // the list of jar files in which we search for user classes.
    static private Collection sysUrls = null;

    // maps rawPath -> URL[]
    private static Map urlCache = Collections.synchronizedMap(new WeakHashMap());
	    
    protected DynamicJavaBridgeClassLoader(ClassLoader parent) {
    	super(parent);
    }
    protected DynamicJavaBridgeClassLoader() {
    	super();
    }
    
    /** Set the library path for the bridge instance. Examples:
     * setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");<br>
     * setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");<br>
     * The first char must be the token separator.
     * @param rawPath The path
     * @param rawContextDir The context dir, e.g. /usr/lib/php/extensions
     */
    public void updateJarLibraryPath(String rawPath, String rawContextDir) {
    	String key = rawPath;
    	rawContextDir += rawContextDir.endsWith("/") ? "lib/" : "/lib/";
	/*
	 * rawPath always starts with a token separator, e.g. ";" 
	 */
	if(rawPath==null || rawPath.length()<2) return;
	// add a token separator if first char is alnum
	char c=rawPath.charAt(0);
	if((c>='A' && c<='Z') || (c>='a' && c<='z') ||
	   (c>='0' && c<='9') || (c!='.' || c!='/'))
	    rawPath = ";" + rawPath;

	/*
	 * Check the cache
	 */
    	URL urls[] = (URL[]) urlCache.get(key);
        if(urls!=null) {
	    try {
		addURLs(rawPath, urls, false); // Uses protected method to explicitly set the classpath entry that is added.
		return;
	    } catch (Exception e) {
		Util.printStackTrace(e);
	    }
	}
        
        /*
         * Parse the path.
         */
    	ArrayList toAdd = new ArrayList();
	String path = rawPath.substring(1);
	StringTokenizer st = new StringTokenizer(path, rawPath.substring(0, 1));
	while (st.hasMoreTokens()) {
	    URL url;
	    String s;
	    s = st.nextToken();

	    try {
		url = new URL(s);
		url.getProtocol();
	    } catch (MalformedURLException e) {
		try {
		    File f=null;
		    StringBuffer buf= new StringBuffer();
		    if((f=new File(s)).isFile() || f.isAbsolute()) {
		    	buf.append(s);
		    } else if ((f=new File(rawContextDir + s)).isFile()) {
		    	buf.append(f.getAbsolutePath());
		    } else if ((f=new File("/usr/share/java/" + s)).isFile()) {
			buf.append(f.getAbsolutePath());
		    } else {
			buf.append(s);
		    }
		    /* From URLClassLoader:
		    ** This class loader is used to load classes and resources from a search
		    ** path of URLs referring to both JAR files and directories. Any URL that
		    ** ends with a '/' is assumed to refer to a directory. Otherwise, the URL
		    *
		    * So we must replace the last backslash with a slash or append a slash
		    * if necessary.
		    */
		    if(f!=null && f.isDirectory()) {
                        addJars(toAdd, f);
		    	int l = buf.length();
		    	if(l>0) {
			    if(buf.charAt(l-1) == File.separatorChar) {
				buf.setCharAt(l-1, '/');
			    } else if(buf.charAt(l-1)!= '/') {
				buf.append('/');
			    }
			}
		    }
		    url = new URL("file", null, buf.toString());
		    url.getProtocol();
		}  catch (MalformedURLException e1) {
		    Util.printStackTrace(e1);
		    continue;
		}
	    }
	    toAdd.add(url);
	}

	urls = new URL[toAdd.size()];
        toAdd.toArray(urls);
	try {
	    addURLs(rawPath, urls, false); // Uses protected method to explicitly set the classpath entry that is added.
            urlCache.put(key, urls);
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
    }
    /*
     * Add all .jar files in a directory
     */
    private void addJars(ArrayList list, File dir) {
	File files[] = dir.listFiles();
	for(int i=0; i<files.length; i++) {
	    File f = files[i];
	    if(f.getName().endsWith(".jar")) {
		try {
		    list.add(new URL("file", null, f.getAbsolutePath()));
		} catch (MalformedURLException e) {
		    Util.printStackTrace(e);
		}
	    }
	}
    }
    /**@deprecated Use #initClassLoader() instead*/
    public static synchronized void initClassLoader(String phpConfigDir) {
      initClassLoader(); 
    }

    /**
     * add all jars found in the phpConfigDir/lib and /usr/share/java
     * to the list of our URLs.  The user is expected to reference .jar
     * libraries explicitly with java_set_library_path, e.g.
     * java_set_library_path("foo.jar;bar.jar"); For backward
     * compatibility we add all URLs we encountered during startup
     * before throwing a "ClassNotFoundException".
     * @param phpConfigDir The default php config directory
     */
    public static synchronized void initClassLoader() {
 	sysUrls=new ArrayList();
	try {
	    String[] paths = new String[] {"/usr/share/java", "/usr/share/java/ext"};

	    for(int i=0; i<paths.length; i++) {
		File d = new File(paths[i]);
		String[] files=d.list();
		if(files==null) continue;
		for(int j=0; j<files.length; j++) {
		    String file = files[j];
		    int len = file.length();
		    if(len<4) continue;
		    if(!file.endsWith(".jar")) continue;
		    try {
			URL url;
			file = "file:" + d.getAbsolutePath() + File.separator + file;
			url = new URL(file);
			if(Util.logLevel>5) 
			  Util.logDebug("Found system library: "+ files[j] +" (url: " + url +").");
			sysUrls.add(url);
		    }  catch (MalformedURLException e1) {
			Util.printStackTrace(e1);
		    }
		}
	    }
	} catch (Exception t) {
	    Util.printStackTrace(t);
	}
    }

    protected void addSysUrls() {
        URL urls[] = new URL[sysUrls.size()];
	synchronized(getClass()) {
            sysUrls.toArray(urls);
	}
	this.addURLs(urls, true);
    }

    /**
     * Clears all caches.
     */
    public void clear() {
    	super.clear();
    	addSysUrls();
    }
    /**
     * Reset to initial state.
     */
    public void reset() {
	synchronized(getClass()) {
	    initClassLoader();
	    clear();
	    clearCache();
	}
    }
    /**
     * Searches for a library name in our classpath
     * @param name the library name, e.g. natcJavaBridge.so
     * @return never returns.  It throws a UnsatisfiedLinkError.
     * @throws UnsatisfiedLinkError
     */
    protected String resolveLibraryName(String name) {
	URL url =  findResource("lib"+name+".so");
	if(url==null) url = findResource(name+".dll");
	if(url!=null) return new File(url.getPath()).getAbsolutePath();
	throw new UnsatisfiedLinkError("Native library " + name + " could not be found in java_require() path.");
    }
    protected URLClassLoaderFactory getUrlClassLoaderFactory() {
    	return new URLClassLoaderFactory() {
    	        public URLClassLoader createUrlClassLoader(String classPath, URL urls[], ClassLoader parent) {
		    return new URLClassLoader(urls, parent) {
    	                    protected Class findClass(String name) throws ClassNotFoundException {
    	                    	if(Util.logLevel>2) Util.logMessage("trying to load class: " +name + " from: "+ Arrays.asList(this.getURLs()));
    	                    	try {
				    return super.findClass(name);
				} catch (Exception e) {
				    throw new ClassNotFoundException("Class " + name + " not found in: " + (Arrays.asList(this.getURLs()))+". Please load all interconnected classes in a single java_require() call, e.g. use java_require(foo;bar) instead of java_require(foo); java_require(bar).", e);
				}
			    }
			    public URL findResource(String name)  {
				//if(Util.logLevel>2) Util.logMessage("trying to load resource: " +name + " from: "+ Arrays.asList(this.getURLs()));
				return super.findResource(name);
    	                	
			    }
			    protected String findLibrary(String name) {
				if(Util.logLevel>2) Util.logMessage("trying to load library: " +name + " from: "+ Arrays.asList(this.getURLs()));
				String s = super.findLibrary(name);
				if(s!=null) return s;
				return resolveLibraryName(name);
			    }
			};
		}
	    };
    }
    /*
     *  (non-Javadoc)
     * @see php.java.bridge.DynamicClassLoader#loadClass(String)
     */
    public Class loadClass(String name) throws ClassNotFoundException {
	try {
	    return super.loadClass(name); 
	} catch (ClassNotFoundException e) {
	    throw new ClassNotFoundException(("Could not find " + name + " in java_require() path"), e);    
	}
    }
    /**
     * Create an instance of the dynamic java bridge classloader
     * It may return null due to security restrictions on certain systems, so don't
     * use this method directly but call: 
     * new JavaBridgeClassLoader(bridge, DynamicJavaBridgeClassLoader.newInstance()) instead.
     */
    public static synchronized DynamicJavaBridgeClassLoader newInstance() {
	try {
	    DynamicJavaBridgeClassLoader cl = new DynamicJavaBridgeClassLoader();
	    cl.setUrlClassLoaderFactory(cl.getUrlClassLoaderFactory());
	    return cl;
	} catch (java.security.AccessControlException e) {
	    return null;
	}
    }
    /**
     * Create an instance of the dynamic java bridge classloader
     * It may return null due to security restrictions on certain systems, so don't
     * use this method directly but call: 
     * new JavaBridgeClassLoader(bridge, DynamicJavaBridgeClassLoader.newInstance()) instead.
     */
    public static synchronized DynamicJavaBridgeClassLoader newInstance(ClassLoader parent) {
	try {
	    DynamicJavaBridgeClassLoader cl = new DynamicJavaBridgeClassLoader(parent);
	    cl.setUrlClassLoaderFactory(cl.getUrlClassLoaderFactory());
	    return cl;
	} catch (java.security.AccessControlException e) {
	    return null;
	}
    }
}
