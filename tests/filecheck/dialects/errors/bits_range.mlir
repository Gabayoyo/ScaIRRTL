// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %a = "scairrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
      %bad = "scairrtl.bits"(%a) <{hi = 9 : i64, lo = 4 : i64}> : (!scairrtl.uint<8>) -> !scairrtl.uint<6>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        %1 = "scairrtl.bits"(%0) <{hi = 9 : i64, lo = 4 : i64}> : (!scairrtl.uint<8>) -> !scairrtl.uint<6>
// CHECK-NEXT:        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:        > Operation 'scairrtl.bits' has a bad bits range: 9 to 4 exceeds the 8-bit input
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }) : () -> ()
