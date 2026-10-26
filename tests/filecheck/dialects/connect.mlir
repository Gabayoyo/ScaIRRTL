// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %src = "firrtl.wire"() <{sym_name = "src", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      %dst = "firrtl.wire"() <{sym_name = "dst", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      "firrtl.matchingconnect"(%dst, %src) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "src", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:        %1 = "firrtl.wire"() <{sym_name = "dst", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:        "firrtl.matchingconnect"(%1, %0) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
