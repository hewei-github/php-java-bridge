/*-*- mode: C; tab-width:4 -*-*/

#ifndef PHP_JAVA_H
#define PHP_JAVA_H

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php_wrapper.h"
#include "php_config.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"
#include "protocol.h"
#ifdef ZTS
#include "TSRM.h"
#endif

/* socket */
#ifdef __MINGW32__
# include <winsock2.h>
# define close closesocket
#else
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#ifndef CFG_JAVA_SOCKET_INET
# include <sys/un.h>
# ifdef HAVE_CONFIG_H
# if !HAVE_DECL_AF_LOCAL
#  define AF_LOCAL AF_UNIX
# endif
# if !HAVE_DECL_PF_LOCAL
#  define PF_LOCAL PF_UNIX
# endif
# endif
#endif
#endif 


extern zend_module_entry EXT_GLOBAL(module_entry);
extern zend_class_entry *EXT_GLOBAL(class_entry);
extern zend_class_entry *EXT_GLOBAL(class_class_entry);
extern zend_class_entry *EXT_GLOBAL(exception_class_entry);
extern function_entry EXT_GLOBAL(class_functions[]);

#ifdef ZEND_ENGINE_2
extern zend_object_handlers EXT_GLOBAL(handlers);
#endif
extern const char * const EXT_GLOBAL(bridge_version);

extern int EXT_GLOBAL(ini_updated), EXT_GLOBAL (ini_last_updated);
#define U_LOGFILE (1<<1)
#define U_LOGLEVEL (1<<2)
#define U_JAVA_HOME (1<<3)
#define U_JAVA (1<<4)
#define U_LIBRARY_PATH (1<<5)
#define U_CLASSPATH (1<<6)
#define U_SOCKNAME (1<<7)
#define U_HOSTS (1<<8)
#define U_SERVLET (1<<9)


#if EXTENSION == JAVA
#define phpext_java_ptr &EXT_GLOBAL(module_entry)
#ifdef PHP_WIN32
#define PHP_JAVA_API __declspec(dllexport)
#else
#define PHP_JAVA_API
#endif
#elif EXTENSION == MONO
#define phpext_mono_ptr &EXT_GLOBAL(module_entry)
#ifdef PHP_WIN32
#define PHP_MONO_API __declspec(dllexport)
#else
#define PHP_MONO_API
#endif
#else
# error EXT must be mono or java.
#endif


PHP_MINIT_FUNCTION(EXT);
PHP_MSHUTDOWN_FUNCTION(EXT);
PHP_MINFO_FUNCTION(EXT);

struct cfg {
#ifdef CFG_JAVA_SOCKET_INET
  struct sockaddr_in saddr;
#else
  struct sockaddr_un saddr;
#endif
  int cid; // server's process id
  int err; // file descriptor: server's return code
  char*sockname;
  char*hosts;
  char*classpath;	
  char*ld_library_path;
  char*vm;
  char*vm_home;
  char*logLevel;
  unsigned short logLevel_val;
  char*logFile;
  short can_fork;				/* 0 if user has hard-coded the socketname */
  char* servlet;				/* On or servlet context */
};
extern struct cfg *EXT_GLOBAL(cfg);

EXT_BEGIN_MODULE_GLOBALS(EXT)
  proxyenv *jenv;
  short is_closed; 				/* PR1176522: GC must not re-open the connection */
EXT_END_MODULE_GLOBALS(EXT)




#ifdef ZTS
# define JG(v) EXT_TSRMG(EXT_GLOBAL(globals_id), EXT_GLOBAL_EX(zend_,, _globals) *, v)
#else
# define JG(v) EXT_GLOBAL(globals).v
#endif

extern char* EXT_GLOBAL(get_server_string());

extern proxyenv *EXT_GLOBAL(try_connect_to_server)(TSRMLS_D);
extern proxyenv *EXT_GLOBAL(connect_to_server)(TSRMLS_D);
extern void EXT_GLOBAL(start_server)();

/* spec: M ono, J ava or I nit (lower-case m or j: no multicast) */
extern char* EXT_GLOBAL(test_server)(int *socket, unsigned char spec);

/* returns the servlet context or null */
extern char *EXT_GLOBAL(get_servlet_context)();

#endif
