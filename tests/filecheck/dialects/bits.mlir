// RUN: scairrtl-opt %s | filecheck %s
// RUN: scairrtl-opt -g %s | scairrtl-opt | filecheck %s --check-prefix=ROUNDTRIP

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %a = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      %hi = "firrtl.bits"(%a) <{hi = 7 : i32, lo = 4 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<4>
      %one = "firrtl.bits"(%a) <{hi = 3 : i32, lo = 3 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<1>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:        %1 = "firrtl.bits"(%0) <{hi = 7 : i32, lo = 4 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<4>
// CHECK-NEXT:        %2 = "firrtl.bits"(%0) <{hi = 3 : i32, lo = 3 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<1>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }

// ROUNDTRIP:       builtin.module {
// ROUNDTRIP-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// ROUNDTRIP-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// ROUNDTRIP-NEXT:        %0 = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// ROUNDTRIP-NEXT:        %1 = "firrtl.bits"(%0) <{hi = 7 : i32, lo = 4 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<4>
// ROUNDTRIP-NEXT:        %2 = "firrtl.bits"(%0) <{hi = 3 : i32, lo = 3 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<1>
// ROUNDTRIP-NEXT:      }) : () -> ()
// ROUNDTRIP-NEXT:    }) : () -> ()
// ROUNDTRIP-NEXT:  }
