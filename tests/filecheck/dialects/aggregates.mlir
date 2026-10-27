// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %w = "firrtl.wire"() <{sym_name = "w", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.bundle<a flip: !firrtl.uint<8>, b: !firrtl.uint<8>>
      %a = "firrtl.subfield"(%w) <{fieldIndex = 0 : i32}> : (!firrtl.bundle<a flip: !firrtl.uint<8>, b: !firrtl.uint<8>>) -> !firrtl.uint<8>
      %v = "firrtl.wire"() <{sym_name = "v", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.vector<!firrtl.uint<8>, 2>
      %e = "firrtl.subindex"(%v) <{index = 0 : i32}> : (!firrtl.vector<!firrtl.uint<8>, 2>) -> !firrtl.uint<8>
      %empty = "firrtl.wire"() <{sym_name = "empty", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.bundle<>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "w", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.bundle<a flip: !firrtl.uint<8>, b: !firrtl.uint<8>>
// CHECK-NEXT:        %1 = "firrtl.subfield"(%0) <{fieldIndex = 0 : i32}> : (!firrtl.bundle<a flip: !firrtl.uint<8>, b: !firrtl.uint<8>>) -> !firrtl.uint<8>
// CHECK-NEXT:        %2 = "firrtl.wire"() <{sym_name = "v", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.vector<!firrtl.uint<8>, 2>
// CHECK-NEXT:        %3 = "firrtl.subindex"(%2) <{index = 0 : i32}> : (!firrtl.vector<!firrtl.uint<8>, 2>) -> !firrtl.uint<8>
// CHECK-NEXT:        %4 = "firrtl.wire"() <{sym_name = "empty", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.bundle<>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
