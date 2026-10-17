// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "scairrtl.layer"() <{sym_name = "Loose", convention = 0 : i32}> ({
  ^bb0:
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "scairrtl.layer"() <{sym_name = "Loose", convention = 0 : i32}> ({
// CHECK-NEXT:    ^bb0:
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:    > Operation 'scairrtl.layer' must be nested in one of CircuitOp, LayerOp
// CHECK-NEXT:  }) : () -> ()
