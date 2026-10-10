// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %src = "scairrtl.wire"() <{sym_name = "src", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
      %dst = "scairrtl.wire"() <{sym_name = "dst", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
      "scairrtl.matchingconnect"(%dst, %src) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> ()
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "src", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        %1 = "scairrtl.wire"() <{sym_name = "dst", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        "scairrtl.matchingconnect"(%1, %0) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> ()
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
