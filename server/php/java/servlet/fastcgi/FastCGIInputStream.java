/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet.fastcgi;

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

import java.io.IOException;

import php.java.bridge.Util;

class FastCGIInputStream extends DefaultInputStream {
    private final FastCGIServlet servlet;
    /**
     * @param servlet
     */
    FastCGIInputStream(FastCGIServlet servlet) {
        this.servlet = servlet;
    }
    private StringBuffer error;
    public StringBuffer getError () {
        return error;
    }
    public String checkError() {
        return error==null?null:Util.checkError(error.toString());
    }
    public int read(byte buf[]) throws ConnectionException {
        try {
	    return doRead(buf);
        } catch (ConnectionException ex) {
	    throw ex;
        } catch (IOException e) {
            throw new ConnectionException(connection, e);
        }
    }
    private byte header[] = new byte[FastCGIServlet.FCGI_HEADER_LEN];
    public int doRead(byte buf[]) throws IOException {
        int n, i;
        //assert if(buf.length!=FCGI_BUF_SIZE) throw new IOException("Invalid block size");
        for(n=0; (i=read(header, n, FastCGIServlet.FCGI_HEADER_LEN-n)) > 0; )  n+=i;
        if(FastCGIServlet.FCGI_HEADER_LEN != n) 
	    throw new IOException ("Protocol error");
        int type = header[1] & 0xFF;
        int contentLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
        int paddingLength = header[6] & 0xFF;
        switch(type) {
        case FastCGIServlet.FCGI_STDERR: 
        case FastCGIServlet.FCGI_STDOUT: {
	    for(n=0; (i=read(buf, n, contentLength-n)) > 0; ) n+=i;
	    if(n!=contentLength) 
		throw new IOException("Protocol error while reading FCGI data");
	    if(type==FastCGIServlet.FCGI_STDERR) { 
		String s = new String(buf, 0, n, Util.ASCII);
		this.servlet.log(s); 
		contentLength = 0;

		if(error==null) error = new StringBuffer(s);
		else error.append(s);
	    }
	    if(paddingLength>0) {
		byte b[] = new byte[paddingLength];
		for(n=0; (i=read(b, n, b.length-n)) > 0; ) n+=i;
		if(n!=paddingLength) 
		    throw new IOException("Protocol error while reading FCGI padding");
	    }
	    return contentLength;
        }
        case FastCGIServlet.FCGI_END_REQUEST: {
	    for(n=0; (i=read(buf, n, contentLength-n)) > 0; ) n+=i;
	    if(n!=contentLength) throw new IOException("Protocol error while reading EOF data");
	    if(paddingLength>0) {
		n = super.read(buf, 0, paddingLength);		
		if(n!=paddingLength) throw new IOException("Protocol error while reading EOF padding");
	    }
	    return -1;
        }
        }
        throw new IOException("Received unknown type");
    }
}