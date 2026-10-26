// RUN: scairrtl-opt %s | filecheck %s
// RUN: scairrtl-opt -g %s | scairrtl-opt | filecheck %s --check-prefix=ROUNDTRIP

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %droppable = "firrtl.wire"() <{sym_name = "droppable", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
      %interesting = "firrtl.wire"() <{sym_name = "interesting", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.uint<1>
      %forced = "firrtl.wire"() <{sym_name = "forced", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "droppable", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
// CHECK-NEXT:        %1 = "firrtl.wire"() <{sym_name = "interesting", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.uint<1>
// CHECK-NEXT:        %2 = "firrtl.wire"() <{sym_name = "forced", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }

// ROUNDTRIP:       builtin.module {
// ROUNDTRIP-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// ROUNDTRIP-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// ROUNDTRIP-NEXT:        %0 = "firrtl.wire"() <{sym_name = "droppable", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
// ROUNDTRIP-NEXT:        %1 = "firrtl.wire"() <{sym_name = "interesting", nameKind = 1 : i32, annotations = []}> : () -> !firrtl.uint<1>
// ROUNDTRIP-NEXT:        %2 = "firrtl.wire"() <{sym_name = "forced", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
// ROUNDTRIP-NEXT:      }) : () -> ()
// ROUNDTRIP-NEXT:    }) : () -> ()
// ROUNDTRIP-NEXT:  }
