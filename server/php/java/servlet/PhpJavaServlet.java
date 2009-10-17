/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

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

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Util;
import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;

/**
 * Handles requests from PHP clients.  <p> When Apache, IIS or php
 * (cli) or php-cgi is used as a front-end, this servlet handles PUT
 * requests and then re-directs to a private (socket- or pipe-)
 * communication channel.  This is the fastest mechanism to connect
 * php and java. It is even 1.5 times faster than local ("unix
 * domain") sockets used by the php.java.bridge.JavaBridge standalone
 * listener.  </p>
 * <p>
 * To enable fcg/servlet debug code start the servlet engine with -Dphp.java.bridge.default_log_level=6.
 * For example: <code>java -Dphp.java.bridge.default_log_level=6 -jar /opt/jakarta-tomcat-5.5.9/bin/bootstrap.jar</code>
 * </p>
 * <p>There cannot be more than one PhpJavaServlet instance per web application. If you extend from this class, make sure to change
 * the .phpjavabridge =&gt; PhpJavaServlet mapping in the WEB-INF/web.xml. </p>
 */
public /*singleton*/ class PhpJavaServlet extends HttpServlet {

    private static final long serialVersionUID = 3257854259629144372L;

    private ContextServer contextServer;
    protected int logLevel = -1;
    private Util.Logger logger;
    protected boolean promiscuous = false;

    // workaround for a bug in weblogic server, see below
    private boolean isWebLogic = false;
    
    /**@inheritDoc*/
    public void init(ServletConfig config) throws ServletException {
 	String servletContextName=CGIServlet.getRealPath(config.getServletContext(), "");
	if(servletContextName==null) servletContextName="";
	ServletContext ctx = config.getServletContext();

	String value = ctx.getInitParameter("promiscuous");
	if(value==null) value="";
	value = value.trim();
	value = value.toLowerCase();
	
	if(value.equals("on") || value.equals("true")) promiscuous=true;
	try { contextServer = getContextServer(ctx, promiscuous); } catch (Throwable e) {/*ignore*/}
    	 
    	super.init(config);
       
    	logger = new Util.Logger(new Logger(ctx));
    	
	if(Util.VERSION!=null)
    	    log("PHP/Java Bridge servlet "+servletContextName+" version "+Util.VERSION+" ready.");
	else
	    log("PHP/Java Bridge servlet "+servletContextName+" ready.");
	
	String name = ctx.getServerInfo();
	if (name != null && (name.startsWith("WebLogic"))) isWebLogic = true;
    }

    /**{@inheritDoc}*/
    public void destroy() {
	ServletContext ctx = getServletContext();
	try {
	    ContextLoaderListener.destroyCloseables(ctx);
	    ContextLoaderListener.destroyScriptEngines(ctx);
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
	
      	if (contextServer != null) contextServer.destroy();
    	super.destroy();
    }
    /**
     * This hook can be used to create a custom context factory. The default implementation checks if there's a ContextFactory 
     * by calling ContextFactory.get(req.getHeader("X_JAVABRIDGE_CONTEXT"), credentials); 
     * If it doesn't exist, a new RemoteServletContextFactory is created.
     * This procedure should set the response header X_JAVABRIDGE_CONTEXT as a side effect.
     * @param req The HttpServletRequest
     * @param res The HttpServletResponse
     * @param credentials The provided credentials.
     * @return The (new) ServletContextFactory.
     */
    protected SimpleServletContextFactory getContextFactory(HttpServletRequest req, HttpServletResponse res, ContextFactory.ICredentials credentials) {
    	JavaBridge bridge;
	SimpleServletContextFactory ctx = null;
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	if(id!=null) ctx = (SimpleServletContextFactory) ContextFactory.get(id, credentials);
    	if(ctx==null) {
    	  ctx = (SimpleServletContextFactory) RemoteServletContextFactory.addNew(this, getServletContext(), null, req, res); // no session sharing
    	  bridge = ctx.getBridge();
    	  bridge.logDebug("HTTP request");
    	} else {
    	    bridge = ctx.getBridge();
    	    bridge.logDebug("redirect");
    	}
    	updateRequestLogLevel(bridge);
    	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	return ctx;
    }
    /**
     * Set the log level from the servlet into the bridge
     * @param bridge The JavaBridge from the ContextFactory.
     */
    protected void updateRequestLogLevel(JavaBridge bridge) {
	if(logLevel>-1) bridge.logLevel = logLevel;
    }

    /**
     * <p>
     * This hook can be used to suspend the termination of the servlet until the (Remote-)ServletContextFactory is finished.
     * It may be useful if one wants to access the Servlet, ServletContext or ServletRequest from a remote PHP script.
     * The notification comes from the php script when it is running as a sub component of the J2EE server or servlet engine.
     * </p>
     * <p>The default is to not wait for a local ServletContextFactory (the ContextFactory is passed from the PhpCGIServlet) 
     * and to wait RemoteContextFactory for 30 seconds.</p>
     * @param ctx The (Remote-) ContextFactory.
     */
    protected void waitForContext(SimpleServletContextFactory ctx) {
	try {
	    ctx.waitFor(Util.MAX_WAIT);
        } catch (InterruptedException e) {
	    Util.printStackTrace(e);
        }
    }

    /**
     * Handle a redirected connection. The local channel is more than 50 
     * times faster than the HTTP tunnel. Used by Apache and cgi.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    protected void handleChunkedLocalConnection (HttpServletRequest req, HttpServletResponse res, String channel)
	throws ServletException, IOException {
	InputStream sin=null; OutputStream sout = null;
	SimpleServletContextFactory ctx = getContextFactory(req, res, null);
	JavaBridge bridge = ctx.getBridge();
	ctx.setSessionFactory(req);
	
	bridge.in = sin=req.getInputStream();
	bridge.out = sout = res.getOutputStream();
	Request r = bridge.request = new Request(bridge);

	if(r.init(sin, sout)) {
	    AbstractChannelName channelName = contextServer.getFallbackChannelName(channel, ctx);
	    res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getName());

	    // start the context runner before generating the first response
	    contextServer.start(channelName, logger);
	    	
	    // generate response
	    r.handleRequests();

	    // redirect and re-open
	    if(bridge.logLevel>3) bridge.logDebug("redirecting to port# "+ channelName);
	    sin.close();
	    try {res.flushBuffer(); } catch (Throwable t) {Util.printStackTrace(t);} // resin ignores resOut.close()
	    try {sout.close(); } catch (Throwable t) {Util.printStackTrace(t);} // Sun Java System AS 9 ignores flushBuffer()
	    this.waitForContext(ctx);
	}
	else {
	    Util.warn("handleChunkedLocalConnection init failed");
	    ctx.destroy();
	}
    }
    /**
     * Handle a redirected connection. The local channel is more than 50 
     * times faster than the HTTP tunnel. Used by Apache and cgi.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    protected void handlePersistentLocalConnection (HttpServletRequest req, HttpServletResponse res, String channel)
	throws ServletException, IOException {
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	SimpleServletContextFactory ctx = getContextFactory(req, res, null);
	JavaBridge bridge = ctx.getBridge();
	ctx.setSessionFactory(req);
	
	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	
	if(r.init(sin, sout)) {
	    AbstractChannelName channelName = contextServer.getFallbackChannelName(channel, ctx);
	    res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getName());

	    // start the context runner before generating the first response
	    contextServer.start(channelName, logger);
	    	
	    // generate response
	    r.handleRequests();

	    // redirect and re-open
	    res.setContentLength(sout.size());
	    resOut = res.getOutputStream();
	    sout.writeTo(resOut);
	    if(bridge.logLevel>3) bridge.logDebug("redirecting to port# "+ channelName);
	    sin.close();
	    try {res.flushBuffer(); } catch (Throwable t) {Util.printStackTrace(t);} // resin ignores resOut.close()
	    try {resOut.close(); } catch (Throwable t) {Util.printStackTrace(t);} // Sun Java System AS 9 ignores flushBuffer()
	    this.waitForContext(ctx);
	}
	else {
	    Util.warn("handlePersistentLocalConnection init failed");
	    ctx.destroy();
	}
    }
    protected void handleLocalConnection (HttpServletRequest req, HttpServletResponse res, String channel)
	throws ServletException, IOException {
	if (getHeader("Content-Length", req)==null) {
	    handleChunkedLocalConnection (req, res, channel);
	} else {
	    handlePersistentLocalConnection (req, res, channel);
	}
    }
   
    /** Only for internal use */
    public static String getHeader(String key, HttpServletRequest req) {
  	String val = req.getHeader(key);
  	if(val==null) return null;
  	if(val.length()==0) val=null;
  	return val;
    }
    private InputStream getInputStream (HttpServletRequest req) throws IOException {
	InputStream in = req.getInputStream();
	if (!isWebLogic) return in;
	
	return new FilterInputStream(in) {
	    /**
	     * Stupid workaround for WebLogic's insane chunked reader implementation, it blocks instead of simply returning what's available so far.
	     * in.getAvailable() can't be used either, because it returns the bytes in weblogics internal cache: For 003\r\n123\r\n weblogic 10.3 returns
	     * 10 instead of 3(!) 
	     */
	    public int read(byte[] buf, int pos, int length) throws IOException {
		return in.read(buf, pos, 1);
	    }
	};
    }
    protected void handleHttpConnection (HttpServletRequest req, HttpServletResponse res) 
	throws ServletException, IOException {
	boolean destroyCtx = false;

	if (getHeader("Content-Length", req)!=null) 
	    log("WARNING: Pipe- and SocketContextServer switched off in the back end. Either enable them or define(\"JAVA_PERSISTENT_SERVLET_CONNECTION\", false) in \"java/Java.inc\" and try again.");

	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
	RemoteHttpServletContextFactory ctx;
	if (id!=null) {
	    ctx = (RemoteHttpServletContextFactory) RemoteHttpServletContextFactory.get(id);
	    if (ctx==null) throw new IllegalStateException("Cannot find RemoteHttpServletContextFactory in session");
	} else {
	    ctx = new RemoteHttpServletContextFactory(this, getServletContext(), req, req, res);
	    destroyCtx = true;
	}
	
	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
	res.setHeader("Pragma", "no-cache");
	res.setHeader("Cache-Control", "no-cache");
	InputStream sin=null; OutputStream sout = null;
	JavaBridge bridge = ctx.getBridge();
    	
	bridge.in = sin = getInputStream (req);
	bridge.out = sout = res.getOutputStream();
	ctx.setResponse (res);
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.init(sin, sout)) {
		r.handleRequests();
	    }
	    else {
		Util.warn("handleHttpConnection init failed");
	    }
	} finally {
	    if (destroyCtx) ctx.destroy();
	}
    }

    private static final String LOCAL_ADDR = "127.0.0.1";

    protected void handlePut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	Util.setLogger(logger);

    	String channel = getHeader("X_JAVABRIDGE_CHANNEL", req);
    	if(Util.logLevel>3) Util.logDebug("doPut:"+req.getRequestURL()); 

    	if(LOCAL_ADDR.equals(req.getRemoteAddr()) && contextServer!=null && contextServer.isAvailable(channel)) 
    	    handleLocalConnection(req, res, channel); /* re-direct */
    	else
    	    handleHttpConnection(req, res);
    }
    
    /**
     * Dispatcher for the "http tunnel", "local channel" or "override redirect".
     */
    protected void doPut (HttpServletRequest req, HttpServletResponse res) 
    	throws ServletException, IOException {
	try {
	    handlePut(req, res);
	} catch (RuntimeException e) {
	    Util.printStackTrace(e);
	    throw new ServletException(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    throw e;
	} catch (ServletException e) {
	    Util.printStackTrace(e);
	    throw e;
	}
    }

    /** For backward compatibility */
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
      		String uri = req.getRequestURI();
     		req.getRequestDispatcher(uri.substring(0, uri.length()-10)).forward(req, res);
    }

    private static final String ROOT_CONTEXT_SERVER_ATTRIBUTE = ContextServer.class.getName()+".ROOT";
    /** Only for internal use */
    public static synchronized ContextServer getContextServer(ServletContext context, boolean promiscuous) {
	ContextServer server = (ContextServer)context.getAttribute(ROOT_CONTEXT_SERVER_ATTRIBUTE);
	if (server == null) {
	    String servletContextName=CGIServlet.getRealPath(context, "");
	    if(servletContextName==null) servletContextName="";
	    server = new ContextServer(servletContextName, promiscuous);
	    context.setAttribute(ROOT_CONTEXT_SERVER_ATTRIBUTE, server);
	}
	return server;
    }
}
