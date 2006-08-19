#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

// the following tests if a class "NoClassDefFound" which contains a
// constructor and a method which internally reference an external
// class "DoesNotExist" can be referenced, and, if an exception is
// thrown if the two classes are loaded by different class loaders.

// make sure to start the backend at log_level 3, to see the "loading..."
// messages.

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_reset();
echo "must succeed\n";
// must succeed
java_require("$here/doesNotExist.jar;$here/noClassDefFound.jar");
$v=new java("NoClassDefFound");
$v->call(null);
exit (java_last_exception_get()?1:0);
?>
