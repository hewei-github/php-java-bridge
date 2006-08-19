#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$class = new java_class("java.lang.Class");
$arr = java_get_values($class->getConstructors());
if(0==sizeof($arr)) {
     echo "test okay\n";
     exit(0);
}
echo "error\n";
exit(1);

?>
