// RUN: scairrtl-opt %s | filecheck %s
// RUN: scairrtl-opt -g %s | scairrtl-opt | filecheck %s --check-prefix=ROUNDTRIP

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %a = "scairrtl.wire"() <{sym_name = "sized", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
      %b = "scairrtl.wire"() <{sym_name = "unsized", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint
      %c = "scairrtl.wire"() <{sym_name = "signed", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.sint<16>
      %d = "scairrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "sized", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        %1 = "scairrtl.wire"() <{sym_name = "unsized", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint
// CHECK-NEXT:        %2 = "scairrtl.wire"() <{sym_name = "signed", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.sint<16>
// CHECK-NEXT:        %3 = "scairrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }

// ROUNDTRIP:       builtin.module {
// ROUNDTRIP-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// ROUNDTRIP-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// ROUNDTRIP-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "sized", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// ROUNDTRIP-NEXT:        %1 = "scairrtl.wire"() <{sym_name = "unsized", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint
// ROUNDTRIP-NEXT:        %2 = "scairrtl.wire"() <{sym_name = "signed", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.sint<16>
// ROUNDTRIP-NEXT:        %3 = "scairrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
// ROUNDTRIP-NEXT:      }) : () -> ()
// ROUNDTRIP-NEXT:    }) : () -> ()
// ROUNDTRIP-NEXT:  }
