#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

try {
  $here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
  java_set_library_path("$here/exception.jar");
  $e = new java("Exception");

  // trigger ID=42
  $i = $e->inner;
  $i->o = new java("java.lang.Integer", 42);

  // should return 33
  $e->inner->meth(33);

  try {
    // should throw exception "Exception$Ex"
    $e->inner->meth(42);
    return 2;
  } catch (java_exception $exception) {
    echo "An exception occured: $exception\n";

    $cause = $exception->getCause();
    echo "exception ". $cause ." --> " . $cause->getID() . "\n";
    return ($cause->getID() == 42) ? 0 : 3; 
  }
} catch (exception $err) {
  print "$err \n";
  return 4;
}
