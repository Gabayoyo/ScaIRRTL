// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top", enable_layers = [@Missing]}> ({
  ^bb0:
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = [], enable_layers = [@Missing]}> ({
// CHECK-NEXT:    ^bb0:
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:    > Operation 'firrtl.circuit' references undefined symbols: @Missing
// CHECK-NEXT:  }) : () -> ()
