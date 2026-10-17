// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %clk = "scairrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
      %rst = "scairrtl.wire"() <{sym_name = "rst", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<1>
      %init = "scairrtl.constant"() <{value = 0 : i64}> : () -> !scairrtl.uint<8>
      %r = "scairrtl.reg"(%clk) <{sym_name = "r", nameKind = 0 : i32, annotations = []}> : (!scairrtl.clock) -> !scairrtl.uint<8>
      %rr = "scairrtl.regreset"(%clk, %rst, %init) <{sym_name = "rr", nameKind = 0 : i32, annotations = []}> : (!scairrtl.clock, !scairrtl.uint<1>, !scairrtl.uint<8>) -> !scairrtl.uint<8>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
// CHECK-NEXT:        %1 = "scairrtl.wire"() <{sym_name = "rst", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<1>
// CHECK-NEXT:        %2 = "scairrtl.constant"() <{value = 0 : i64}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        %3 = "scairrtl.reg"(%0) <{sym_name = "r", nameKind = 0 : i32, annotations = []}> : (!scairrtl.clock) -> !scairrtl.uint<8>
// CHECK-NEXT:        %4 = "scairrtl.regreset"(%0, %1, %2) <{sym_name = "rr", nameKind = 0 : i32, annotations = []}> : (!scairrtl.clock, !scairrtl.uint<1>, !scairrtl.uint<8>) -> !scairrtl.uint<8>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
