/*-*- mode: Java; tab-width:8 -*-*/
package php.java.servlet.fastcgi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import php.java.bridge.ILogger;
import php.java.bridge.Util;
import php.java.bridge.Util.Process;
import php.java.servlet.ServletUtil;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

/**
 * A factory which creates FastCGI channels.
 * @author jostb
 */
public abstract class ChannelFactory {
    protected FastCGIServlet servlet;
    protected String contextPath;
    protected boolean promiscuous;
    
    /* The fast CGI Server process on this computer. Switched off per default. */
    protected static FCGIProcess proc = null;
    private static boolean fcgiStarted = false;
    private static final Object fcgiStartLock = new Object(); // one lock for all servlet intances of this class loader
    
    
    /**
     * Start the FastCGI server
     * @return false if the FastCGI server failed to start.
     */
    protected final boolean startServer(ILogger logger) {
	/*
	 * Try to start the FastCGI server,
	 */
	synchronized(ChannelFactory.fcgiStartLock) {
	    if(!fcgiStarted) {
		    if(canStartFCGI()) 
			try {
			    bind(logger);
			} catch (Exception e) {/*ignore*/}
		
		fcgiStarted = true; // mark as started, even if start failed
	    } 
	}
	return fcgiStarted;
    }
    /**
     * Test the FastCGI server.
     * @throws ConnectException thrown if a IOException occured.
     */
    public abstract void test() throws ConnectException;
    
    protected abstract void waitForDaemon() throws UnknownHostException, InterruptedException;
    protected final void runFcgi(Map env, String php, boolean includeJava) {
	int c;
	byte buf[] = new byte[Util.BUF_SIZE];
	try {
	    Process proc = doBind(env, php, includeJava);
	    if(proc==null || proc.getInputStream() == null) return;
	    /// make sure that the wrapper script launcher.sh does not output to stdout
	    proc.getInputStream().close();
	    // proc.OutputStream should be closed in shutdown, see PhpCGIServlet.destroy()
	    InputStream in = proc.getErrorStream();
	    while((c=in.read(buf))!=-1) System.err.write(buf, 0, c);
	    try { in.close(); } catch (IOException e) {/*ignore*/}
	} catch (Exception e) {System.err.println("Could not start FCGI server: " + e);};
    }

    protected abstract Process doBind(Map env, String php, boolean includeJava) throws IOException;
    protected void bind(final ILogger logger) throws InterruptedException, IOException {
	Thread t = (new Util.Thread("JavaBridgeFastCGIRunner") {
		public void run() {
	            Util.setLogger(logger);
		    Map env = (Map) FastCGIServlet.PROCESS_ENVIRONMENT.clone();
		    env.put("PHP_FCGI_CHILDREN", servlet.php_fcgi_connection_pool_size);
		    env.put("PHP_FCGI_MAX_REQUESTS", servlet.php_fcgi_max_requests);
		    runFcgi(env, servlet.php, servlet.php_include_java);
		}
	    });
	t.start();
	waitForDaemon();
    }

    private boolean canStartFCGI() {
	return servlet.canStartFCGI;
    }
	
    void destroy() {
	synchronized(ChannelFactory.fcgiStartLock) {
	    fcgiStarted = false;
	    if(proc==null) return;  	
	    try {
		OutputStream out = proc.getOutputStream();
		if (out != null) out.close();
	    } catch (IOException e) {
		Util.printStackTrace(e);
	    }
	    try {
		proc.waitFor();
	    } catch (InterruptedException e) {
		// ignore
	    }
	    proc.destroy();
	    proc=null;
	}
    }

    /**
     * Connect to the FastCGI server and return the connection handle.
     * @return The FastCGI Channel
     * @throws ConnectException thrown if a IOException occured.
     */
    public abstract Channel connect() throws ConnectException;

    /**
     * For backward compatibility the "JavaBridge" context uses the port 9667 (Linux/Unix) or <code>\\.\pipe\JavaBridge@9667</code> (Windogs).
     * @param servlet The servlet
     * @param req The current request.
     * @param contextPath The path of the web context
     */
    public void initialize(FastCGIServlet servlet, HttpServletRequest req, String contextPath) {
	this.servlet = servlet;
	this.contextPath = contextPath;
	if(ServletUtil.isJavaBridgeWc(contextPath)) {
	    setDefaultPort();
	} else {
	    setDynamicPort();
	}
    }
    protected abstract void setDynamicPort();
    protected abstract void setDefaultPort();

    /**
     * Return a command which may be useful for starting the FastCGI server as a separate command.
     * @param base The context directory
     * @param php_fcgi_max_requests The number of requests, see appropriate servlet option.
     * @return A command string
     */
    public abstract String getFcgiStartCommand(String base, String php_fcgi_max_requests);
	
    /**
     * Find a free port or pipe name. 
     * @param select If select is true, the default name should be used.
     */
    public abstract void findFreePort(boolean select);

    /**
     * Create a new ChannelFactory.
     * @return The concrete ChannelFactory (NP or Socket channel factory).
     */
    public static ChannelFactory createChannelFactory(boolean promiscuous) {
	if(Util.USE_SH_WRAPPER)
	    return new SocketChannelFactory(promiscuous);
	else 
	    return new NPChannelFactory();
    }
	
    /** 
     * Return the channel name 
     * @return the channel name
     * 
     */
    public String toString() {
	return "ChannelName@" + contextPath==null ? "<not initialized>" : contextPath;
    }
}