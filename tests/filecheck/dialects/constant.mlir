// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %zero = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
      %neg = "firrtl.constant"() <{value = -3 : i64}> : () -> !firrtl.sint<4>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
// CHECK-NEXT:        %1 = "firrtl.constant"() <{value = -3 : i64}> : () -> !firrtl.sint<4>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
