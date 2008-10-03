/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.script.PhpScriptException;
import php.java.script.URLReader;
import php.java.servlet.CGIServlet;
import php.java.servlet.ContextLoaderListener;
import php.java.servlet.PhpCGIServlet;

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
 * A PHP script engine for Servlets. See {@link ContextLoaderListener} for details.
 * 
 * In order to evaluate PHP methods follow these steps:<br>
 * <ol>
 * <li> Create a factory which creates a PHP script file from a reader using the methods from {@link EngineFactory}:
 * <blockquote>
 * <code>
 * private static File script;<br>
 * private static final File getHelloScript() {<br>
 * &nbsp;&nbsp; if (script!=null) return script;<br><br>
 * &nbsp;&nbsp; String webCacheDir = ctx.getRealPath(req.getServletPath());<br>
 * &nbsp;&nbsp; Reader reader = new StringReader ("&lt;?php echo 'hello from PHP'; ?&gt;");<br>
 * &nbsp;&nbsp; return EngineFactory.getPhpScript(webCacheDir, reader);<br>
 * }<br>
 * </code>
 * </blockquote>
 * <li> Aquire a PHP script engine from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getPhpScriptEngine(this, ctx, req, res);
 * </code>
 * </blockquote> 
 * <li> Create a FileReader for the created script file:
 * <blockquote>
 * <code>
 * Reader readerHello = EngineFactory.createPhpScriptFileReader(getHelloScript());
 * </code>
 * </blockquote>
 * <li> Connect its output:
 * <blockquote>
 * <code>
 * scriptEngine.getContext().setWriter (out);
 * </code>
 * </blockquote>
 * <li> Evaluate the engine:
 * <blockquote>
 * <code>
 * scriptEngine.eval(readerHello);
 * </code>
 * </blockquote> 
 * <li> Close the reader:
 * <blockquote>
 * <code>
 * readerHello.close();
 * </code>
 * </blockquote> 
 * </ol>
 * <br>
 * Alternatively one may use the following "quick and dirty" code which creates a new PHP script for 
 * each eval:
 * <blockquote>
 * <code>
 * ScriptEngine e = EngineFactory.getPhpScriptEngine(this, ctx, req, res);<br>
 * e.getContext().setWriter (out);<br>
 * e.eval("&lt;?php echo "hello java world"; ?&gt;");<br>
 * </code>
 * </blockquote>
 */


public class PhpServletScriptEngine extends PhpServletLocalScriptEngine {
    private File path;
    public PhpServletScriptEngine(Servlet servlet, 
				  ServletContext ctx, 
				  HttpServletRequest req, 
				  HttpServletResponse res) throws MalformedURLException {
	super (servlet, ctx, req, res);
	path = new File(CGIServlet.getRealPath(ctx, ""));
    }
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {

	// use a short path if the script file already exists
	if (reader instanceof ScriptFileReader) return super.eval(reader, context, name);
	
    	File tempfile = null;
    	FileOutputStream fout = null;
        Reader localReader = null;
  	if(reader==null) return null;
  	
  	setNewContextFactory();
        setName(name);
        
        try {
	    fout = new FileOutputStream(tempfile=File.createTempFile("tempfile", ".php", path));
	    OutputStreamWriter writer = new OutputStreamWriter(fout);
	    char[] cbuf = new char[Util.BUF_SIZE];
	    int length;

	    while((length=reader.read(cbuf, 0, cbuf.length))>0) 
		writer.write(cbuf, 0, length);
	    writer.close();

	    String filePath = req.getContextPath()+"/"+tempfile.getName();
	    url = new java.net.URI(url.getProtocol(), null, url.getHost(), url.getPort(), filePath, null, null).toURL();
	    
            /* now evaluate our script */
	    
	    PhpCGIServlet.reserveContinuation(); //  engines need a PHP- and an optional Java continuation
	    localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw new PhpScriptException("Could not close script", e);}
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } catch (URISyntaxException e) {
            Util.printStackTrace(e);
        } finally {
            PhpCGIServlet.releaseReservedContinuation();
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            if(fout!=null) try { fout.close(); } catch (IOException e) {/*ignore*/}
            if(tempfile!=null) tempfile.delete();
            release ();
        }
	return null;
    }
}
