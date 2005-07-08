m4_include(tests.m4/function_checks.m4)
m4_include(tests.m4/threads.m4)
m4_include(tests.m4/java_check_broken_stdio_buffering.m4)
m4_include(tests.m4/java_check_struct_ucred.m4)
m4_include(tests.m4/java_check_abstract_namespace.m4)
m4_include(tests.m4/java_check_broken_gcc_installation.m4)

PHP_ARG_WITH(java, for java support,
[  --with-java[=JAVA_HOME]        Include java support])
AC_ARG_WITH(mono,  [  --with-mono[[=ikvmc.exe location]]             Include mono support], PHP_MONO="$withval", PHP_MONO="no")
PHP_ARG_ENABLE(servlet, for java servlet support,
[  --enable-servlet[=JAR]         Include java servlet support. JAR must be the location of j2ee.jar or servlet.jar; creates JavaBridge.war])


if test "$PHP_JAVA" != "no" || test "$PHP_MONO" != "no"  ; then
       JAVA_FUNCTION_CHECKS
       PTHREADS_CHECK
       PTHREADS_ASSIGN_VARS
       PTHREADS_FLAGS
       JAVA_CHECK_BROKEN_STDIO_BUFFERING
       JAVA_CHECK_BROKEN_GCC_INSTALLATION
       JAVA_CHECK_ABSTRACT_NAMESPACE
       JAVA_CHECK_STRUCT_UCRED
       if test "$have_broken_gcc_installation" = "yes"; then
         AC_MSG_WARN([YOUR GCC INSTALLATION IS BROKEN. It tries to link with the same library for -m32 and -m64 builds. This may result in a "wrong ELF class" error at runtime. Although you can work around this bug at runtime by changing the LD_LIBRARY_PATH, we recommend to re-install the gcc compiler before you continue to install the PHP/Java Bridge.])
	  sleep 30
       fi

# find includes eg. -I/opt/jdk1.4/include -I/opt/jdk1.4/include/linux
        if test "$PHP_JAVA" != "yes"; then
	 PHP_EVAL_INCLINE(`for i in \`find $PHP_JAVA/include -follow -type d -print\`; do echo -n "-I$i "; done`)
	 COND_GCJ=0
	else
	 COND_GCJ=1
	fi

        if test "$PHP_MONO" != "no";then 
# create mono.so, compile with -DEXTENSION_DIR="\"$(EXTENSION_DIR)\""
	PHP_NEW_EXTENSION(mono, php_java_snprintf.c java.c java_bridge.c client.c parser.c protocol.c bind.c init_cfg.c ,$ext_shared,,[-DEXTENSION_DIR=\"\\\\\"\\\$(EXTENSION_DIR)\\\\\"\"])
          EXTENSION_NAME=MONO
	  PHP_JAVA_BIN="/usr/bin/mono"
	  COND_GCJ=0
          PHP_JAVA=${EXTENSION_DIR}
        else 
# create java.so, compile with -DEXTENSION_DIR="\"$(EXTENSION_DIR)\""
	PHP_NEW_EXTENSION(java, php_java_snprintf.c java.c java_bridge.c client.c parser.c protocol.c bind.c init_cfg.c ,$ext_shared,,[-DEXTENSION_DIR=\"\\\\\"\\\$(EXTENSION_DIR)\\\\\"\"])
          EXTENSION_NAME=JAVA
	  PHP_JAVA_BIN="${PHP_JAVA}/bin/java"
        fi

# create init_cfg.c from the template (same as AC_CONFIG_FILES)
	BRIDGE_VERSION="`cat $ext_builddir/VERSION`"
        for i in init_cfg.c init_cfg.h; do 
	  sed "s*@PHP_JAVA@*${PHP_JAVA}*
	     s*@COND_GCJ@*${COND_GCJ}*
             s*@PHP_JAVA_BIN@*${PHP_JAVA_BIN}*
             s*@EXTENSION@*${EXTENSION_NAME}*
             s*@BRIDGE_VERSION@*${BRIDGE_VERSION}*" \
            <$ext_builddir/${i}.in >$ext_builddir/${i}
        done

# bootstrap the server's configure script
	if test -d ext/java/server; then
	    AC_CONFIG_SUBDIRS(ext/java/server)
        else
	    AC_CONFIG_SUBDIRS(server)
        fi
        for i in ${ext_builddir}/server/configure.gnu php-java-bridge.fc update_policy.sh; do
          sed "s*@EXTENSION_DIR@*${EXTENSION_DIR}*
               s*@phplibdir@*`pwd`/modules*" \
            <${i}.in >${i}
        done

# an artificial target so that the server/ part gets compiled
	PHP_ADD_MAKEFILE_FRAGMENT
	PHP_SUBST(JAVA_SHARED_LIBADD)
	PHP_MODULES="$PHP_MODULES \$(phplibdir)/libnatcJavaBridge.la"

fi
