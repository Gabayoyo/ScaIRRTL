// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %clk = "firrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
      %rst = "firrtl.wire"() <{sym_name = "rst", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
      %init = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
      %r = "firrtl.reg"(%clk) <{sym_name = "r", nameKind = 0 : i32, annotations = []}> : (!firrtl.clock) -> !firrtl.uint<8>
      %rr = "firrtl.regreset"(%clk, %rst, %init) <{sym_name = "rr", nameKind = 0 : i32, annotations = []}> : (!firrtl.clock, !firrtl.uint<1>, !firrtl.uint<8>) -> !firrtl.uint<8>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
// CHECK-NEXT:        %1 = "firrtl.wire"() <{sym_name = "rst", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<1>
// CHECK-NEXT:        %2 = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
// CHECK-NEXT:        %3 = "firrtl.reg"(%0) <{sym_name = "r", nameKind = 0 : i32, annotations = []}> : (!firrtl.clock) -> !firrtl.uint<8>
// CHECK-NEXT:        %4 = "firrtl.regreset"(%0, %1, %2) <{sym_name = "rr", nameKind = 0 : i32, annotations = []}> : (!firrtl.clock, !firrtl.uint<1>, !firrtl.uint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
