sinclude (php_check_broken_stdio_buffering.m4)

AC_DEFUN(JAVA_FUNCTION_CHECKS,[

 AC_CHECK_FUNCS(longjmp perror socket snprintf tempnam \
  strerror memcpy memmove sigset pthread_sigmask \
  pthread_attr_setdetachstate pthread_attr_create)

 PHP_CHECK_BROKEN_STDIO_BUFFERING
])