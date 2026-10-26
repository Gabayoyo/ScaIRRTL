// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "firrtl.module"() <{sym_name = "Foo", port_directions = [], port_types = [!firrtl.uint<8>, !firrtl.sint<16>], port_names = ["a", "b"], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
  ^bb0:
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [!firrtl.uint<8>, !firrtl.sint<16>], sym_name = "Foo", annotations = [], port_names = ["a", "b"], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:    ^bb0:
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:    > Operation 'firrtl.module' must be nested in CircuitOp
// CHECK-NEXT:  }) : () -> ()
