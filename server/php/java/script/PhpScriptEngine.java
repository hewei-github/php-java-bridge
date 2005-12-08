/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.util.Map;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import php.java.bridge.NotImplementedException;
import php.java.bridge.PhpProcedure;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;

/**
 * This class implements the ScriptEngine.<p>
 * Example:<p>
 * <code>
 * ScriptEngine e = new PhpScriptEngine();<br>
 * e.eval(new URLReader(new URL("http://localhost/foo.php"));<br>
 * System.out.println(((Invocable)e).call("java_get_server_name", new Object[]{}));<br>
 * e.release();<br>
 * </code>
 * @author jostb
 *
 */
public class PhpScriptEngine extends AbstractScriptEngine implements Invocable {

	
    /**
     * The allocated script
     */
    protected PhpProcedureProxy script = null;
    protected Object scriptClosure = null;
	
    /**
     * The continuation of the script
     */
    protected HttpProxy continuation = null;

    /**
     * Create a new ScriptEngine.
     */
    public PhpScriptEngine() {
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object[])
     */
    public Object invoke(String methodName, Object[] args)
	throws ScriptException {
	return invoke(scriptClosure, methodName, args);
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object, java.lang.Object[])
     */
    public Object invoke(Object thiz, String methodName, Object[] args)
	throws ScriptException, RuntimeException {
	PhpProcedure proc = (PhpProcedure)(Proxy.getInvocationHandler(thiz));
	try {
	    return proc.invoke(script, methodName, args);
	} catch (Throwable e) {
	    if(e instanceof RuntimeException) throw (RuntimeException)e;
	    Util.printStackTrace(e);
	    if(e instanceof Exception) throw new ScriptException(new Exception(e));
	    throw (RuntimeException)e;
	}
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#getInterface(java.lang.Class)
     */
    public Object getInterface(Class clasz) {
	return getInterface(script, clasz);
    }
    /* (non-Javadoc)
     * @see javax.script.Invocable#getInterface(java.lang.Object, java.lang.Class)
     */
    public Object getInterface(Object thiz, Class clasz) {
	return ((PhpProcedureProxy)thiz).getNewFromInterface(clasz);
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.io.Reader, javax.script.ScriptContext)
     */
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
	try {
	    this.script = doEval(reader, context);
	} catch (Exception e) {
	    throw new ScriptException(e);
	}
		
	if(this.script==null) throw new ScriptException("The script from "+reader+" is not invocable. To change this please add a line \"java_context()->call(java_closure());\" at the bottom of the script.");
		
	try {
	    this.scriptClosure = this.script.getProxy(new Class[]{});
	} catch (Exception e) {
	    throw new ScriptException(e);
	}
	return null;
    }
	
    protected HttpProxy getContinuation(Reader reader, ScriptContext context) {
    	IPhpScriptContext phpScriptContext = (IPhpScriptContext)context;
    	SessionFactory ctx = phpScriptContext.getContextManager();
    	Map env = phpScriptContext.getEnvironment();
    	HttpProxy kont = new HttpProxy(reader, env, ctx, ((PhpScriptWriter)(context.getWriter())).getOutputStream()); 
     	phpScriptContext.setContinuation(kont);
	return kont;
    }

    /*
     * Obtain a PHP instance for url.
     */
    protected PhpProcedureProxy doEval(Reader reader, ScriptContext context) throws UnknownHostException, IOException, InterruptedException {
    	continuation = getContinuation(reader, context);

     	continuation.start();
    	return continuation.getPhpScript();
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.lang.String, javax.script.ScriptContext)
     */
    public Object eval(String script, ScriptContext context)
	throws ScriptException {
	try {
	    return eval(new FileReader(new File(script)), context);
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    throw new ScriptException(e);
	}
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#getFactory()
     */
    public ScriptEngineFactory getFactory() {
	throw new NotImplementedException();
    }

    protected ScriptContext getScriptContext(Bindings namespace) {
        ScriptContext scriptContext = new PhpScriptContext();
        
        if(namespace==null) namespace = createBindings();
        scriptContext.setBindings(namespace,ScriptContext.ENGINE_SCOPE);
        scriptContext.setBindings(getBindings(ScriptContext.GLOBAL_SCOPE),
				   ScriptContext.GLOBAL_SCOPE);
        
        return scriptContext;
    }    
    
    /**
     * Release the continuation
     */
    public void release() {
	if(continuation != null) {
	    continuation.release();
	    continuation = null;
	    script = null;
	    scriptClosure = null;
	}
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#createBindings()
     */
    /** {@inheritDoc} */
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    /** {@inheritDoc} */
   public Object eval(Reader reader, Bindings bindings ) throws ScriptException {
        return eval(reader, getScriptContext(bindings));
    }
    
    /** {@inheritDoc} */
    public Object eval(String script, Bindings bindings) throws ScriptException {
        return eval(script , getScriptContext(bindings));
    }
    /** {@inheritDoc} */
    public Object eval(Reader reader) throws ScriptException {
        return eval(reader, getScriptContext(null));
    }
    /** {@inheritDoc} */
     public Object eval(String script) throws ScriptException {
        return eval(script, getScriptContext(null));
    }
 
 }