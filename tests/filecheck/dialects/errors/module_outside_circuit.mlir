// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "scairrtl.module"() <{sym_name = "Foo", port_directions = [], port_types = [!scairrtl.uint<8>, !scairrtl.sint<16>], port_names = ["a", "b"], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
  ^bb0:
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [!scairrtl.uint<8>, !scairrtl.sint<16>], sym_name = "Foo", annotations = [], port_names = ["a", "b"], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:    ^bb0:
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:    > Operation 'scairrtl.module' must be nested in CircuitOp
// CHECK-NEXT:  }) : () -> ()
