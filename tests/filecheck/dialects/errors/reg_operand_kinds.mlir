// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %notclk = "firrtl.wire"() <{sym_name = "notclk", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
      %r = "firrtl.reg"(%notclk) <{sym_name = "r", nameKind = 0 : i32, annotations = []}> : (!firrtl.uint<1>) -> !firrtl.uint<8>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "notclk", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
// CHECK-NEXT:        %1 = "firrtl.reg"(%0) <{sym_name = "r", nameKind = 0 : i32, annotations = []}> : (!firrtl.uint<1>) -> !firrtl.uint<8>
// CHECK-NEXT:        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:        > Operation 'firrtl.reg' has ill-typed operands or results: firrtl.uint, expected ClockType
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }) : () -> ()
