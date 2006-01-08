/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * This class is used to write the response to the frontent.
 * 
 * @author jostb
 *
 */
public class Response {

    /**
     * A specialized writer which writes arrays as values.
     * Used by getValues() and in php 4.
     * @see JavaBridge#getValues(Object)
     */
    public static final int VALUES_WRITER = 1;
    
    /**
     * A specialized writer which casts the value.
     * Used by cast().
     * @see JavaBridge#cast(Object, Class)
     */
    public static final int COERCE_WRITER = 2;

    // used in getFirstBytes() only
    static final byte append_for_OutBuf_getFirstBytes[] = new byte[] {'.', '.', '.' }; 
    static final byte append_none_for_OutBuf_getFirstBytes[] = new byte[0]; 

    private class OutBuf extends ByteArrayOutputStream {
	
    	/*
	 * Return up to 256 bytes. Useful for logging.
	 */
	byte[] getFirstBytes() {
	    int c = super.count;
	    byte[] append = (c>256) ? append_for_OutBuf_getFirstBytes : append_none_for_OutBuf_getFirstBytes;
	    if(c>256) c=256;
	    byte[] ret = new byte[c+append.length];
	    System.arraycopy(super.buf, 0, ret, 0, c);
	    System.arraycopy(append, 0, ret, ret.length-append.length, append.length);
	    return ret;
	}

    	void append(byte[] s) {
	    try {
		write(s);
	    } catch (IOException e) {/*not possible*/}
	}
    	void append(String s) {
	    try {
		this.write(s.getBytes()); //used for objects only, not for UTF8 strings
	    } catch (IOException e) {/*not possible*/}
	}
    	void appendQuoted(byte[] s) {
	    for(int i=0; i<s.length; i++) {
		byte ch;
		switch(ch=s[i]) {
		case '&':
		    append(amp);
		    break;
		case '\"':
		    append(quote);
		    break;
		default:
		    write(ch);
		}
	    }
    	}
    	void appendQuoted(String s) {
	    appendQuoted(bridge.options.getBytes(s));
    	}
    }
    OutBuf buf;
    long result, peer;
    private JavaBridge bridge;

 
    protected abstract class DelegateWriter {
    	protected Class staticType;

	public abstract boolean setResult(Object value);

	/**
	 * @param type - The result type
	 */
	public void setType(Class type) {
	    this.staticType = type;
	}
    }
    protected abstract class Writer extends DelegateWriter {
	public void setResult(Object value, Class type) {
	    setType(type);
	    setResult(value);
	}        
    }
    protected abstract class WriterWithDelegate extends Writer {
	protected DelegateWriter delegate;

	public void setType(Class type) {
	    super.setType(type);
	    delegate.setType(type);
	}
    }
    protected class ArrayWriter extends DelegateWriter {
	public boolean setResult(Object value) {
		return false;
	}
    }
    protected class ArrayValuesWriter extends DelegateWriter {
	public boolean setResult(Object value) {
	    if (value.getClass().isArray()) {
		long length = Array.getLength(value);
		writeCompositeBegin_a();
		for (int i=0; i<length; i++) {
		    writePairBegin();
		    writer.setResult(Array.get(value, i));
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.Map) {
		Map ht = (Map) value;
		writeCompositeBegin_h();
		for (Iterator e = ht.entrySet().iterator(); e.hasNext(); ) {
		    Map.Entry entry = (Map.Entry)e.next();
		    Object key = entry.getKey();
		    Object val = entry.getValue();
		    if (key instanceof Number &&
			!(key instanceof Double || key instanceof Float)) {
			writePairBegin_n(((Number)key).intValue());
			writer.setResult(val);
		    }
		    else {
			writePairBegin_s(String.valueOf(key));
			writer.setResult(ht.get(key));
		    }
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.List) {
		List ht = (List) value;
		writeCompositeBegin_h();
		for (ListIterator e = ht.listIterator(); e.hasNext(); ) {
		    int key = e.nextIndex();
		    Object val = e.next();
		    writePairBegin_n(key);
		    writer.setResult(val);
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else {
		return false;
	    }
	    return true;
        }
    }
    protected class ClassicArrayValuesWriter extends DelegateWriter {
	public boolean setResult(Object value) {
	    if (value.getClass().isArray()) {
		long length = Array.getLength(value);
		writeCompositeBegin_a();
		for (int i=0; i<length; i++) {
		    writePairBegin();
		    writer.setResult(Array.get(value, i));
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.Map) {
		Map ht = (Map) value;
		writeCompositeBegin_h();
		for (Iterator e = ht.entrySet().iterator(); e.hasNext(); ) {
		    Map.Entry entry = (Map.Entry)e.next();
		    Object key = entry.getKey();
		    Object val = entry.getValue();
		    if (key instanceof Number &&
			!(key instanceof Double || key instanceof Float)) {
			writePairBegin_n(((Number)key).intValue());
			writer.setResult(val);
		    }
		    else {
			writePairBegin_s(String.valueOf(key));
			writer.setResult(ht.get(key));
		    }
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else {
		return false;
	    }
	    return true;
        }
    }
    protected class ClassicWriter extends WriterWithDelegate {
 
	public boolean setResult(Object value) {
	    if (value == null) {
		writeNull();
	    } else if (value instanceof byte[]) {
		writeString((byte[])value);
	    } else if (value instanceof java.lang.String) {
		writeString((String)value);
	    } else if (value instanceof Request.PhpString) {
	        writeString(((Request.PhpString)value).getBytes());
	    } else if (value instanceof java.lang.Number) {

		if (value instanceof java.lang.Integer ||
		    value instanceof java.lang.Short ||
		    value instanceof java.lang.Byte) {
		    writeLong(((Number)value).longValue());
		} else {
		    /* Float, Double, BigDecimal, BigInteger, Double, Long, ... */
		    writeDouble(((Number)value).doubleValue());
		}

	    } else if (value instanceof java.lang.Boolean) {
		writeBoolean(((Boolean)value).booleanValue());

	    } else if(!delegate.setResult(value))
		writeObject(value);
	    return true;
	}
    }
    protected class DefaultWriter extends WriterWithDelegate {

	public boolean setResult(Object value) {
	    if(value==null) {writeNull(); return true;}
	    if(staticType.isPrimitive()) {
   		if(staticType == Boolean.TYPE)
		    writeBoolean(((Boolean) value).booleanValue());
   		else if(staticType == Byte.TYPE || staticType == Short.TYPE || staticType == Integer.TYPE)
		    writeLong(((Number)value).longValue());
   		else if(staticType == Long.TYPE)
   		    writeDouble(((Number)value).doubleValue());
   		else if(staticType == Float.TYPE || staticType == Double.TYPE) 
		    writeDouble(((Number)value).doubleValue());
   		else if(staticType == Character.TYPE) 
		    writeString(String.valueOf(value));
   		else { Util.logFatal("Unknown type"); writeObject(value); }
	    } else if(value instanceof Request.PhpString) //  No need to check for Request.PhpNumber, this cannot happen.
	        writeString(((Request.PhpString)value).getBytes());
	    else if(!delegate.setResult(value))
		writeObject(value);
	    return true;
        }        	     	
    }
  	
    protected class CoerceWriter extends Writer {
	public void setResult(Object value, Class resultType) {
	    // ignore resultType and use the coerce type
	    setResult(value);
	}

	public boolean setResult(Object value) {
		 if(value instanceof Request.PhpString) 
		     value = ((Request.PhpString)value).getString();

		 if(staticType.isPrimitive()) {
		if(staticType == Boolean.TYPE) {
		    if(value instanceof Boolean)
		        writeBoolean(((Boolean) value).booleanValue());
		    else 
		        writeBoolean(value!=null && String.valueOf(value).length()!=0);
		} else if(staticType == Byte.TYPE || staticType == Short.TYPE || staticType == Integer.TYPE || staticType == Long.TYPE) {
		    if(value instanceof Number) 
		        writeLong(((Number)value).longValue());
		    else {
		        try { writeLong(new Long(String.valueOf(value)).longValue()); } catch (NumberFormatException n) { writeLong(0); }
		    }
		} else if(staticType == Float.TYPE || staticType == Double.TYPE) {
		    if(value instanceof Number) 
		        writeDouble(((Number)value).doubleValue());
		    else {
		        try { writeDouble(new Double(String.valueOf(value)).doubleValue()); } catch (NumberFormatException n) { writeDouble(0.0); }
		    }
		} else if(staticType == Character.TYPE) {
		    writeString(String.valueOf(value));
		} else { Util.logFatal("Unknown type"); writeObject(value); }
	    } else if(staticType == String.class) {
		 if (value instanceof byte[])
		     writeString((byte[])value);
		 else
		     writeString(String.valueOf(value));
	    } else {
		writeObject(value);
	    }
	    return true;
	}
    }
    DelegateWriter getDefaultDelegate() {
	if(bridge.options.sendArraysAsValues())
	    return new ClassicArrayValuesWriter();
	else
	    return new ArrayWriter();
    }
	
    WriterWithDelegate newWriter(DelegateWriter delegate) {
     	WriterWithDelegate writer;
    	if(bridge.options.extJavaCompatibility())
	    writer = new ClassicWriter();
    	else 
	    writer = new DefaultWriter();
    	writer.delegate = delegate;
	return writer;
    }
     
    private Writer writer, defaultWriter, arrayValuesWriter=null, coerceWriter=null;

    
    /**
     * Creates a new response object. The object is re-used for each packed.
     * @param bridge The bridge.
     */
    public Response(JavaBridge bridge) {
	buf=new OutBuf();
	this.bridge=bridge;
	defaultWriter = writer = newWriter(getDefaultDelegate());
    }

    /**
      * Set the result packet.
      * @param value The result object.
      * @param type The type of the result object.
      */
    public void setResult(Object value, Class type) {
     	writer.setResult(value, type);
    }

    /**
     * Selects a different writer.
     * @param writerType Must be Response#VALUES_WRITER or Response#COERCE_WRITER.
     * @return The seleted writer.
     * @see Response#VALUES_WRITER
     * @see Response#COERCE_WRITER
     */
    public Writer selectWriter(int writerType) {
     	switch(writerType) {
    	case VALUES_WRITER: writer = getArrayValuesWriter(); break;
    	case COERCE_WRITER: writer = getCoerceWriter(); break;
    	default: throw new IllegalArgumentException(String.valueOf(writerType));
    	}
     	return writer;
    }
    void setResultID(long id) {
    	this.result=id;
    }
    
   
    static final byte[] e="\"/>".getBytes();
    static final byte[] c="\">".getBytes();
    static final byte[] I="\" i=\"".getBytes();
    static final byte[] S="<S v=\"".getBytes();
    static final byte[] B="<B v=\"".getBytes();
    static final byte[] L="<L v=\"".getBytes();
    static final byte[] D="<D v=\"".getBytes();
    static final byte[] E="<E v=\"".getBytes();
    static final byte[] O="<O v=\"".getBytes();
    static final byte[] N="<N i=\"".getBytes();
    static final byte[] m="\" m=\"".getBytes();
    static final byte[] n="\" n=\"".getBytes();
    static final byte[] p="\" p=\"".getBytes();
    static final byte[] pa="\" p=\"A".getBytes();
    static final byte[] po="\" p=\"O".getBytes();
    static final byte[] Xa="<X t=\"A".getBytes();
    static final byte[] Xh="<X t=\"H".getBytes();
    static final byte[] Xe="</X>".getBytes();
    static final byte[] A="<A v=\"".getBytes();
    static final byte[] Ae="</A>".getBytes();
    static final byte[] P="<P>".getBytes();
    static final byte[] Pn="<P t=\"N\" v=\"".getBytes();
    static final byte[] Ps="<P t=\"S\" v=\"".getBytes();
    static final byte[] Pe="</P>".getBytes();
    static final byte[] quote="&quot;".getBytes();
    static final byte[] amp="&amp;".getBytes();

    void writeString(byte s[]) {

	buf.append(S);
	buf.appendQuoted(s);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeString(String s) {
	writeString(bridge.options.getBytes(s));
    }
    void writeBoolean(boolean b) {
	buf.append(B); buf.write(b==true?'T':'F');
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeLong(long l) {
	buf.append(L);
	buf.append(String.valueOf(l));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeDouble(double d) {
	buf.append(D); buf.append(String.valueOf(d));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    static final String emptyString="";
    void writeNull() {
	buf.append(N);
	buf.append(String.valueOf(result));
	buf.append(e);
    }
    private boolean isArray(Class type) {
        return type.isArray() ||  
        List.class.isAssignableFrom(type) || 
        Map.class.isAssignableFrom(type);
    }
    void writeObject(Object o) {
        if(o==null) { writeNull(); return; }
        Class dynamicType = o.getClass();
	buf.append(O); buf.append(String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(isArray(dynamicType)?pa:po);
	buf.append(n); buf.append("0");
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    public void writeClass(Class o) {
        if(o==null) { writeNull(); return; }
    	buf.append(O); buf.append(String.valueOf(this.bridge.globalRef.append(o)));
    	buf.append(po);
    	buf.append(n); buf.append("0");
    	buf.append(I); buf.append(String.valueOf(result));
    	buf.append(e);
    }
    void writeException(Object o, String str) {
	buf.append(E); buf.append(String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(m); buf.appendQuoted(String.valueOf(str));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeCompositeBegin_a() {
	buf.append(Xa);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(c);
    }
    void writeCompositeBegin_h() {
	buf.append(Xh);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(c);
    }
    void writeCompositeEnd() {
	buf.append(Xe);
    }
    void writePairBegin_s(String key) {
	buf.append(Ps);	buf.appendQuoted(key);
	buf.append(c);
    }
    void writePairBegin_n(int key) {
	buf.append(Pn); buf.append(String.valueOf(key));
	buf.append(c);
    }
    void writePairBegin() {
	buf.append(P);
    }
    void writePairEnd() {
	buf.append(Pe);
    }
    void writeApplyBegin(long object, String pos, String str, int argCount) {
 	buf.append(A); buf.append(String.valueOf(object));
 	buf.append(p); buf.appendQuoted(String.valueOf(pos));
 	buf.append(m); buf.appendQuoted(String.valueOf(str));
 	buf.append(n); buf.append(String.valueOf(argCount));
 	buf.append(I); buf.append(String.valueOf(result));
 	buf.append(c);
    }
    void writeApplyEnd() {
	buf.append(Ae);
    }
    
    /**
     * Write the response.
     * @throws IOException
     */
    public void flush() throws IOException {
 	if(bridge.logLevel>=4) {
	    bridge.logDebug(" <-- " +bridge.options.newString(buf.getFirstBytes()));
	}
	buf.writeTo(bridge.out);
	reset();
    }
    
    /**
     * Called at the end of each packed.
     */
    protected void reset() {
    	writer = defaultWriter;
    	buf.reset();
    }
    public String toString() {
    	return bridge.options.newString(buf.getFirstBytes());
    }

    private Writer getArrayValuesWriter() {
        if(arrayValuesWriter==null) {
            WriterWithDelegate writer = new ClassicWriter();
            writer.delegate = new ArrayValuesWriter();
            arrayValuesWriter = writer;
        }
        return arrayValuesWriter;
    }

    private Writer getCoerceWriter() {
        if(coerceWriter==null) return coerceWriter = new CoerceWriter();
        return coerceWriter;
    }

}
