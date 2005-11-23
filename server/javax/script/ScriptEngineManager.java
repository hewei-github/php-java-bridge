
package javax.script;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import sun.misc.Service;

/**
 * ScriptEngineManager implements a discovery and instantiation 
 * mechanisams for ScriptEngine class. It also contains a collection 
 * of key-value pairs which storing state shared by all engines 
 * created by manager.
 * 
 * Nandika Jayawardana <nandika@opensource.lk>
 * Sanka Samaranayake  <sanka@opensource.lk> 
 */
public class ScriptEngineManager {

	/** Stores all instances of classes which implements 
     * ScriptEngineFactory which are found in resources 
     * META-INF/services/javax.script.ScriptEngineFactory
     */
    protected HashSet engineSpis = new HashSet();
	
    /**
     * Stores language names with an associated 
     * ScriptEngineFactory 
     */     
	protected HashMap nameAssociations = new HashMap();
	
    /** 
     * Stores file extensions with an associated 
     * ScriptEngineFactory 
     */
	protected HashMap extensionAssocitions = new HashMap();
	
    /** Stores MIME types with an associated ScriptEngineFactory */
	protected HashMap mimeTypeAssociations = new HashMap();
	
    /** Stores the namespace associated with GLOBAL_SCOPE */
	protected Bindings globalscope = new SimpleBindings();
	
    /**
     * Constructs ScriptEngineManager and initializes it.
     */
	public ScriptEngineManager() {
		
        Iterator iterator = Service.providers(ScriptEngineFactory.class);
     	
        while (iterator.hasNext()) {
			ScriptEngineFactory factory = (ScriptEngineFactory) iterator.next();
			engineSpis.add(factory);
            
            String[] data = factory.getNames();
            // gets all descriptinve names for Scripting Engine
            for (int i=0; i<data.length; i++) {
                nameAssociations.put(data[i], factory);
            }
            // gets all supported extensions 
            data = factory.getExtensions();
            for (int i=0; i<data.length; i++) {
                extensionAssocitions.put(data[i], factory);
            }
            // gets all supported MIME types
            data = factory.getMimeTypes();
            for (int i=0; i<data.length; i++) {
                mimeTypeAssociations.put(data[i], factory);
            }            
		}
	}
	
    /**
     * Retrieves the associated value for the spefied key in the 
     * GLOBAL_SCOPE
     *  
     * @param key the associated key of the value stored in the 
     *        GLOBAL_SCOPE
     * @return the value associated with the specifed key 
     */
    public Object get(String key){
        return globalscope.get(key);    
    }

	/**
     * Retrieves a new instance of a ScriptingEngine for the 
     * specified extension of a scirpt file. Returns null if no 
     * suitable ScriptingEngine is found.
     * 
	 * @param extension the specified extension of a script file
	 * @return a new instance of a ScriptingEngine which supports the
     *         specified script file extension
	 */
    public ScriptEngine getEngineByExtention(String extension){
        
        ScriptEngine engine = null;
        
        ScriptEngineFactory factory = 
                (ScriptEngineFactory) extensionAssocitions.get(extension);
        
		if (factory != null) {
            // gets a new instance of the Scripting Engine
			engine = factory.getScriptEngine();
            // sets the GLOBAL SCOPE
			engine.setNamespace(globalscope,ScriptContext.GLOBAL_SCOPE);
        }
        
		return engine;
	}
	
	/**
     * Retrieves new instance the ScriptingEngine for a specifed MIME
     * type. Returns null if no suitable ScriptingEngine is found.
     * 
	 * @param mimeType the specified MIME type
	 * @return a new instance of a ScriptingEngine which supports the
     *         specified MIME type  
	 */
    public ScriptEngine getEngineByMimeType(String mimeType){
        
        ScriptEngine engine = null;
		ScriptEngineFactory factory = 
                (ScriptEngineFactory) mimeTypeAssociations.get(mimeType);
		
		if (factory != null) {
			// gets a new instance of the Scripting Engine
            engine = factory.getScriptEngine();
            // sets the GLOBAL SCOPE
			engine.setNamespace(globalscope,ScriptContext.GLOBAL_SCOPE);
        }
        
		return engine;
	}
	
    /**
     * Retrieves a new instance of a ScriptEngine the specified 
     * descriptieve name. Returns null if no suitable ScriptEngine is
     * found.
     * 
     * @param name the descriptive name 
     * @return a new instance of a ScriptEngine which supports the 
     *         specifed descriptive name
     */
	public ScriptEngine getEngineByName(String name){
		
        ScriptEngine engine = null;
        ScriptEngineFactory factory =
                (ScriptEngineFactory) nameAssociations.get(name);
		
		if (factory != null) {
            engine = factory.getScriptEngine();
			engine.setNamespace(globalscope,ScriptContext.GLOBAL_SCOPE);
		}
        
		return engine; 
	}
    
    /**
     * Retrieves an array of instances of ScriptEngineFactory class 
     * which are found by the discovery mechanism.
     * 
     * @return an array of all discovered ScriptEngineFactory 
     *         instances 
     */
    public ScriptEngineFactory[] getEngineFactories(){
        return (ScriptEngineFactory[])engineSpis.toArray();
    }
    
    /**
     * Retrieves the namespace corresponds to GLOBAL_SCOPE.
     * 
     * @return the namespace of GLOBAL_SCOPE
     */
    public Bindings getNameSpace(){
            return globalscope;
    }
    
    /**
     * Associates the specifed value with the specified key in 
     * GLOBAL_SCOPE.
     * 
     * @param key the associated key for specified value 
     * @param value the associated value for the specified key
     */
    public void put(String key,Object value){
            globalscope.put(key,value);
    }
	
    /**
     * Register a extension with a ScriptEngineFactory class. It 
     * overrides any such association discovered previously.
     * 
     * @param extension the extension associated with the specified
     *        ScriptEngineFactory class
     * @param factory the ScriptEngineFactory class associated with
     *        the specified extension
     */
    public void registerEngineExtension(String extension, Class factory){
        extensionAssocitions.put(extension, factory);        
    }
    
    /**
     * Registers descriptive name with a ScriptEngineFactory class. 
     * It overrides any associations discovered previously.
     * 
     * @param name a descriptive name associated with the specifed 
     *        ScriptEngineFactory class
     * @param factory the ScriptEngineFactory class associated with
     *        the specified descriptive name
     */
    public void registerEngineName(String name, Class factory){
        nameAssociations.put(name, factory);
    }
    
    /**
     * Registers a MIME type with a ScriptEngineFactory class. It 
     * overrides any associations discovered previously.
     *  
     * @param mimeType the MIME type associated with specified 
     *        ScriptEngineFactory class 
     * @param factory the ScriptEngineFactory associated with the
     *        specified MIME type
     */
	public void registerEngineMimeType(String mimeType,Class factory){
		mimeTypeAssociations.put(mimeType,factory);
	}
		
    /**
     * Sets the GLOBAL_SCOPE value to the specified namespace.
     * 
     * @param namespace the namespace to be stored in GLOBAL_SCOPE 
     */
	public void setNamespace(Bindings namespace){
		globalscope = namespace;
	}
    

} 