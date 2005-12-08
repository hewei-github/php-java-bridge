<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}


class helloWorld {
  var $name="myName";

  function getValue($prop) {
    return $this->$prop;
  }

  function setValue($prop, $val) {
    $this->$prop = $val;
  }

  function send() {
    return "success";
  }

  function xvalidate($ctx, $arg, $value) {
    echo "validate: $value";
    if($value->equals("myName")) {
      echo "throw exception.";
      $message = new Java("javax.faces.application.FacesMessage", "$value invalid, enter yourname");
      throw 
	new JavaException("javax.faces.validator.ValidatorException", $message);
    }
  }
}

// call() returns true if the php file was called from java.
java_context()->call(java_closure(new helloWorld())) ||include("index.php");
?>