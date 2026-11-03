// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %addr = "firrtl.wire"() <{sym_name = "addr", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      %clk = "firrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
      %ram = "chirrtl.combmem"() <{sym_name = "ram", nameKind = 1 : i32, init = #firrtl.meminit<"mem.hex", 0, 0>}> : () -> !chirrtl.cmemory<!firrtl.uint<8>, 16>
      %d, %p = "chirrtl.memoryport"(%ram) <{direction = 1 : i32, sym_name = "port"}> : (!chirrtl.cmemory<!firrtl.uint<8>, 16>) -> (!firrtl.uint<8>, !chirrtl.cmemoryport)
      "chirrtl.memoryport.access"(%p, %addr, %clk) : (!chirrtl.cmemoryport, !firrtl.uint<8>, !firrtl.clock) -> ()
      %sram = "chirrtl.seqmem"() <{ruw = 1 : i32, sym_name = "sram", nameKind = 1 : i32, annotations = []}> : () -> !chirrtl.cmemory<!firrtl.uint<8>, 4>
      %dbg = "chirrtl.debugport"(%sram) <{sym_name = "dbg"}> : (!chirrtl.cmemory<!firrtl.uint<8>, 4>) -> !firrtl.probe<!firrtl.vector<!firrtl.uint<8>, 4>>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "addr", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:        %1 = "firrtl.wire"() <{sym_name = "clk", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
// CHECK-NEXT:        %2 = "chirrtl.combmem"() <{sym_name = "ram", nameKind = 1 : i32, annotations = [], init = #firrtl.meminit<"mem.hex", 0 : i64, 0 : i64>}> : () -> !chirrtl.cmemory<!firrtl.uint<8>, 16>
// CHECK-NEXT:                                                                                                  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:                                                                                                  > meminit flags must be i1 attributes
// CHECK-NEXT:        %3, %4 = "chirrtl.memoryport"(%2) <{direction = 1 : i32, sym_name = "port", annotations = []}> : (!chirrtl.cmemory<!firrtl.uint<8>, 16>) -> (!firrtl.uint<8>, !chirrtl.cmemoryport)
// CHECK-NEXT:        "chirrtl.memoryport.access"(%4, %0, %1) : (!chirrtl.cmemoryport, !firrtl.uint<8>, !firrtl.clock) -> ()
// CHECK-NEXT:        %5 = "chirrtl.seqmem"() <{ruw = 1 : i32, sym_name = "sram", nameKind = 1 : i32, annotations = []}> : () -> !chirrtl.cmemory<!firrtl.uint<8>, 4>
// CHECK-NEXT:        %6 = "chirrtl.debugport"(%5) <{sym_name = "dbg", annotations = []}> : (!chirrtl.cmemory<!firrtl.uint<8>, 4>) -> !firrtl.probe<!firrtl.vector<!firrtl.uint<8>, 4>>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }) : () -> ()

