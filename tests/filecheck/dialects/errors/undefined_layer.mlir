// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top", enable_layers = [@Missing]}> ({
  ^bb0:
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = [], enable_layers = [@Missing]}> ({
// CHECK-NEXT:    ^bb0:
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:    > Operation 'scairrtl.circuit' references undefined symbols: @Missing
// CHECK-NEXT:  }) : () -> ()
